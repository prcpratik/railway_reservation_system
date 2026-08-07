package com.eureka.railway.service;

import com.eureka.railway.dto.Dtos.BookingResponse;
import com.eureka.railway.dto.Dtos.PassengerRequest;
import com.eureka.railway.dto.Dtos.PaymentRequest;
import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.Passenger;
import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainStop;
import com.eureka.railway.entity.User;
import com.eureka.railway.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BookingService {

    public static final int MAX_PASSENGERS = 6;

    // how many people may wait for one class on one train on one date
    public static final int MAX_WAITLIST_PER_CLASS = 10;

    private final BookingRepository bookingRepository;
    private final TrainService trainService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public BookingService(BookingRepository bookingRepository, TrainService trainService,
            UserService userService, PaymentService paymentService,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.trainService = trainService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    @Transactional
    public BookingResponse book(String userEmail, Long trainId, SeatClass seatClass, LocalDate journeyDate,
            List<PassengerRequest> passengers, boolean allowWaitlist,
            String fromStation, String toStation) {
        validatePassengers(passengers);
        if (seatClass == null) {
            throw new IllegalArgumentException("Select a travel class");
        }
        if (journeyDate == null || journeyDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Journey date cannot be in the past");
        }
        User user = userService.getByEmail(userEmail);
        // lock the train row first: concurrent bookings for the same train now
        // queue up here, so the seat count and seat numbers below are accurate
        Train train = trainService.getByIdForUpdate(trainId);

        TrainClass trainClass = train.findClass(seatClass).orElseThrow(
                () -> new IllegalArgumentException("This train does not have " + seatClass.getLabel() + " class"));

        if (!train.getRunsOn().contains(journeyDate.getDayOfWeek())) {
            throw new IllegalArgumentException(train.getName() + " does not run on "
                    + journeyDate.getDayOfWeek().name().charAt(0)
                    + journeyDate.getDayOfWeek().name().substring(1).toLowerCase() + "s");
        }

        // which part of the route is being travelled, and what it costs
        Segment segment = resolveSegment(train, fromStation, toStation);
        double farePerSeat = Math.round(trainClass.getFare() * segment.fareShare());

        int booked = bookingRepository.countBookedSeats(trainId, seatClass, journeyDate);
        int available = Math.max(0, trainClass.getTotalSeats() - booked);
        int toConfirm = Math.min(available, passengers.size());
        int toWaitlist = passengers.size() - toConfirm;

        int alreadyWaiting = bookingRepository.countWaitlisted(trainId, seatClass, journeyDate);
        if (toWaitlist > 0) {
            // never put someone on the waitlist without them asking for it
            if (!allowWaitlist) {
                throw new IllegalArgumentException("Only " + available + " seats available in "
                        + seatClass.getLabel() + ". You can join the waitlist instead.");
            }
            if (alreadyWaiting + toWaitlist > MAX_WAITLIST_PER_CLASS) {
                throw new IllegalArgumentException("The waitlist for " + seatClass.getLabel()
                        + " is full. Please try another class or date.");
            }
        }

        // still inside the lock, so two bookings can never get the same seat number
        List<Integer> seatNumbers = allocateSeatNumbers(
                bookingRepository.findTakenSeatNumbers(trainId, seatClass, journeyDate),
                trainClass.getTotalSeats(), toConfirm);

        Booking booking = new Booking(user, train, seatClass, journeyDate, farePerSeat);
        booking.setFromStation(segment.fromStation());
        booking.setToStation(segment.toStation());
        booking.setFromStopOrder(segment.fromOrder());
        booking.setToStopOrder(segment.toOrder());
        for (int i = 0; i < passengers.size(); i++) {
            PassengerRequest p = passengers.get(i);
            Passenger passenger = new Passenger(p.name().trim(), p.age(), p.gender());
            if (i < toConfirm) {
                passenger.setSeatNumber(seatNumbers.get(i));
            } else {
                // no seat yet - joins the queue behind everyone already waiting
                passenger.setStatus("WAITLISTED");
                passenger.setWaitlistNumber(alreadyWaiting + (i - toConfirm) + 1);
            }
            booking.addPassenger(passenger);
        }
        // waitlisted passengers pay up front like on IRCTC; they are refunded if
        // they cancel, so the fare covers every passenger that is not cancelled
        booking.setTotalFare(passengers.size() * farePerSeat);
        booking.setPnr(generatePnr());
        recompute(booking);

        if (paymentService.isEnabled()) {
            // seat is held, but the ticket is confirmed only after payment
            booking.setStatus("PENDING");
            booking.setRazorpayOrderId(paymentService.createOrder(booking.getTotalFare(), "PNR_" + booking.getPnr()));
        }
        Booking saved = bookingRepository.save(booking);
        // if payment is disabled the booking is already confirmed, so notify now;
        // otherwise the confirmation email is sent from confirmPayment()
        if ("CONFIRMED".equals(saved.getStatus())) {
            notificationService.sendBookingConfirmation(saved);
        }
        return BookingResponse.from(saved);
    }

    // picks the lowest free seat numbers in 1..capacity for this train+class+date.
    // A cancelled passenger is not "taken", so their seat is offered again.
    private List<Integer> allocateSeatNumbers(List<Integer> takenSeats, int capacity, int count) {
        Set<Integer> taken = new HashSet<>(takenSeats);
        List<Integer> allotted = new ArrayList<>();
        for (int seat = 1; seat <= capacity && allotted.size() < count; seat++) {
            if (!taken.contains(seat)) {
                allotted.add(seat);
            }
        }
        if (allotted.size() < count) { // availability was already checked, so this is a safety net
            throw new IllegalArgumentException("Not enough seats available");
        }
        return allotted;
    }

    // the part of the route being booked, plus what share of the full fare it costs
    private record Segment(String fromStation, String toStation, int fromOrder, int toOrder, double fareShare) {
    }

    // Works out which stops the passenger boards and leaves at. Defaults to the
    // whole route when no stations are given (or the train has no route listed).
    private Segment resolveSegment(Train train, String fromStation, String toStation) {
        List<TrainStop> stops = train.getStops();
        if (stops.isEmpty()) {
            return new Segment(train.getSource(), train.getDestination(), 0, 0, 1.0);
        }
        TrainStop first = stops.get(0);
        TrainStop last = stops.get(stops.size() - 1);
        TrainStop from = isBlank(fromStation) ? first : findStop(train, fromStation);
        TrainStop to = isBlank(toStation) ? last : findStop(train, toStation);

        if (from.getStopOrder() >= to.getStopOrder()) {
            throw new IllegalArgumentException(
                    "This train travels " + first.getStation() + " to " + last.getStation()
                            + ", so it does not go from " + from.getStation() + " to " + to.getStation());
        }
        return new Segment(from.getStation(), to.getStation(),
                from.getStopOrder(), to.getStopOrder(), train.fareShare(from, to));
    }

    private TrainStop findStop(Train train, String station) {
        return train.findStop(station).orElseThrow(() -> new IllegalArgumentException(
                "This train does not stop at " + station.trim()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // random 10-digit ticket number; retry on the rare collision with an existing
    // one
    private String generatePnr() {
        String pnr;
        do {
            pnr = String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                    .nextLong(1_000_000_000L, 10_000_000_000L));
        } while (bookingRepository.existsByPnr(pnr));
        return pnr;
    }

    // called after the Razorpay checkout succeeds; verifies the signature
    // server-side
    @Transactional
    public BookingResponse confirmPayment(Long bookingId, String userEmail, PaymentRequest payment) {
        Booking booking = getOwnBooking(bookingId, userEmail);
        if (!"PENDING".equals(booking.getStatus())) {
            throw new IllegalArgumentException("This booking is not awaiting payment");
        }
        boolean valid = booking.getRazorpayOrderId() != null
                && booking.getRazorpayOrderId().equals(payment.razorpayOrderId())
                && paymentService.verifySignature(payment.razorpayOrderId(),
                        payment.razorpayPaymentId(), payment.razorpaySignature());
        if (!valid) {
            throw new IllegalArgumentException("Payment verification failed");
        }
        booking.setRazorpayPaymentId(payment.razorpayPaymentId());
        // clear PENDING first so recompute derives the real status (CONFIRMED or
        // WAITLISTED)
        booking.setStatus("CONFIRMED");
        recompute(booking);
        Booking saved = bookingRepository.save(booking);
        notificationService.sendBookingConfirmation(saved);
        return BookingResponse.from(saved);
    }

    // readOnly transaction so the lazily loaded passenger list can be read while
    // building the DTO, without relying on the open-in-view session
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(String userEmail) {
        User user = userService.getByEmail(userEmail);
        return bookingRepository.findByUserIdOrderByIdDesc(user.getId())
                .stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> allBookings() {
        List<Booking> bookings = bookingRepository.findAllByOrderByIdDesc();
        List<BookingResponse> responses = new ArrayList<>(bookings.size());
        for (Booking booking : bookings) {
            responses.add(BookingResponse.from(booking));
        }
        return responses;
    }

    // partial cancellation: cancel one passenger, the seat is freed and fare
    // reduced
    @Transactional
    public BookingResponse cancelPassenger(Long bookingId, Long passengerId, String userEmail) {
        Booking booking = getOwnBooking(bookingId, userEmail);
        if ("PENDING".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Payment is pending - pay first or cancel the whole booking");
        }

        Passenger passenger = null;
        for (Passenger p : booking.getPassengers()) {
            if (p.getId().equals(passengerId)) {
                passenger = p;
                break;
            }
        }

        if (passenger == null) {
            throw new IllegalArgumentException("Passenger not found in this booking");
        }

        if ("CANCELLED".equals(passenger.getStatus())) {
            throw new IllegalArgumentException("Passenger is already cancelled");
        }

        // lock the train: freeing this seat may promote someone off the waitlist,
        // which allocates a seat number and must not run twice in parallel
        Train train = trainService.getByIdForUpdate(booking.getTrain().getId());

        // full refund of this one seat's fare
        double refunded = refund(booking, booking.getFarePerSeat());
        passenger.setStatus("CANCELLED");
        passenger.setSeatNumber(null);
        passenger.setWaitlistNumber(null);
        recompute(booking);
        Booking saved = bookingRepository.save(booking);
        promoteFromWaitlist(train, booking.getSeatClass(), booking.getJourneyDate());
        notificationService.sendCancellation(saved, refunded);
        return BookingResponse.from(saved);
    }

    // full cancellation: cancel all remaining passengers
    @Transactional
    public BookingResponse cancel(Long bookingId, String userEmail) {
        Booking booking = getOwnBooking(bookingId, userEmail);
        if ("CANCELLED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Booking is already cancelled");
        }
        Train train = trainService.getByIdForUpdate(booking.getTrain().getId());

        // refund every seat not cancelled yet - waitlisted passengers paid too
        long stillActive = 0;
        for (Passenger p : booking.getPassengers()) {
            if (!"CANCELLED".equals(p.getStatus())) {
                stillActive++;
            }
        }

        double refunded = refund(booking, stillActive * booking.getFarePerSeat());
        for (Passenger p : booking.getPassengers()) {
            p.setStatus("CANCELLED");
            p.setSeatNumber(null);
            p.setWaitlistNumber(null);
        }
        recompute(booking);
        Booking saved = bookingRepository.save(booking);
        promoteFromWaitlist(train, booking.getSeatClass(), booking.getJourneyDate());
        notificationService.sendCancellation(saved, refunded);
        return BookingResponse.from(saved);
    }

}
