package com.eureka.railway.service;

import com.eureka.railway.dto.Dtos.BookingResponse;
import com.eureka.railway.dto.Dtos.PassengerRequest;
import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.Passenger;
import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.User;
import com.eureka.railway.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// unit test of the service layer with Mockito (repositories are mocked, no DB needed)
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TrainService trainService;

    @Mock
    private UserService userService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BookingService bookingService;

    private static final double FARE = 150.0;
    private static final int CAPACITY = 100;

    private User user;
    private Train train;
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        user = new User("Test User", "test@mail.com", "encoded", "USER");
        train = new Train("12126", "Pragati Express", "Pune", "Mumbai", "07:15", "10:45");
        train.setId(1L);
        train.addClass(new TrainClass(SeatClass.AC3, CAPACITY, FARE));
    }

    private List<PassengerRequest> twoPassengers() {
        return List.of(new PassengerRequest("Amit", 25, "Male"),
                       new PassengerRequest("Neha", 23, "Female"));
    }

    @Test
    void bookSucceedsWhenSeatsAvailable() {
        when(userService.getByEmail("test@mail.com")).thenReturn(user);
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.countBookedSeats(1L, SeatClass.AC3, tomorrow)).thenReturn(98);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.existsByPnr(any())).thenReturn(false);
        when(paymentService.isEnabled()).thenReturn(false);

        BookingResponse booking = bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow, twoPassengers(), false, null, null);

        assertEquals("CONFIRMED", booking.status());
        assertEquals(2, booking.passengers().size());
        assertEquals(300.0, booking.totalFare());
        assertEquals(10, booking.pnr().length()); // 10-digit ticket number
    }

    @Test
    void bookFailsWhenNotEnoughSeats() {
        when(userService.getByEmail("test@mail.com")).thenReturn(user);
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.countBookedSeats(1L, SeatClass.AC3, tomorrow)).thenReturn(99);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow, twoPassengers(), false, null, null));
        assertTrue(e.getMessage().contains("1 seats available"));
    }

    @Test
    void bookGoesToWaitlistWhenClassIsFull() {
        when(userService.getByEmail("test@mail.com")).thenReturn(user);
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.countBookedSeats(1L, SeatClass.AC3, tomorrow)).thenReturn(CAPACITY); // full
        when(bookingRepository.countWaitlisted(1L, SeatClass.AC3, tomorrow)).thenReturn(0);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.existsByPnr(any())).thenReturn(false);
        when(paymentService.isEnabled()).thenReturn(false);

        BookingResponse booking = bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow,
                twoPassengers(), true, null, null);

        assertEquals("WAITLISTED", booking.status());
        assertEquals(1, booking.passengers().get(0).waitlistNumber());
        assertEquals(2, booking.passengers().get(1).waitlistNumber());
        assertNull(booking.passengers().get(0).seatNumber());
        // waitlisted passengers pay too, so the fare covers both
        assertEquals(300.0, booking.totalFare());
    }

    @Test
    void bookConfirmsWhatItCanAndWaitlistsTheRest() {
        when(userService.getByEmail("test@mail.com")).thenReturn(user);
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.countBookedSeats(1L, SeatClass.AC3, tomorrow)).thenReturn(CAPACITY - 1); // 1 free
        when(bookingRepository.countWaitlisted(1L, SeatClass.AC3, tomorrow)).thenReturn(2); // 2 already waiting
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.existsByPnr(any())).thenReturn(false);
        when(paymentService.isEnabled()).thenReturn(false);

        BookingResponse booking = bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow,
                twoPassengers(), true, null, null);

        assertEquals("WAITLISTED", booking.status()); // not fully confirmed yet
        assertEquals("CONFIRMED", booking.passengers().get(0).status());
        assertEquals("WAITLISTED", booking.passengers().get(1).status());
        assertEquals(3, booking.passengers().get(1).waitlistNumber()); // joins behind the existing two
    }

    @Test
    void waitlistIsRejectedWhenTheQueueIsFull() {
        when(userService.getByEmail("test@mail.com")).thenReturn(user);
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.countBookedSeats(1L, SeatClass.AC3, tomorrow)).thenReturn(CAPACITY);
        when(bookingRepository.countWaitlisted(1L, SeatClass.AC3, tomorrow))
                .thenReturn(BookingService.MAX_WAITLIST_PER_CLASS);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow, twoPassengers(), true, null, null));
        assertTrue(e.getMessage().contains("waitlist"));
    }

    @Test
    void bookFailsForPastDate() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.book("test@mail.com", 1L, SeatClass.AC3, LocalDate.now().minusDays(1), twoPassengers(), false, null, null));
    }

    @Test
    void bookFailsWithoutPassengers() {
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.book("test@mail.com", 1L, SeatClass.AC3, tomorrow, List.of(), false, null, null));
    }

    @Test
    void partialCancelReducesFareAndSetsStatus() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE);
        Passenger p1 = new Passenger("Amit", 25, "Male");
        p1.setId(11L);
        Passenger p2 = new Passenger("Neha", 23, "Female");
        p2.setId(12L);
        booking.addPassenger(p1);
        booking.addPassenger(p2);
        booking.setTotalFare(300.0);

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse result = bookingService.cancelPassenger(5L, 11L, "test@mail.com");

        assertEquals("PARTIALLY_CANCELLED", result.status());
        assertEquals(150.0, result.totalFare());

        // cancelling the last passenger cancels the whole booking
        result = bookingService.cancelPassenger(5L, 12L, "test@mail.com");
        assertEquals("CANCELLED", result.status());
        assertEquals(0.0, result.totalFare());
    }

    @Test
    void paymentConfirmsPendingBooking() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE);
        booking.addPassenger(new Passenger("Amit", 25, "Male"));
        booking.setStatus("PENDING");
        booking.setRazorpayOrderId("order_123");

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.verifySignature("order_123", "pay_9", "sig_ok")).thenReturn(true);

        BookingResponse result = bookingService.confirmPayment(5L, "test@mail.com",
                new com.eureka.railway.dto.Dtos.PaymentRequest("pay_9", "order_123", "sig_ok"));

        assertEquals("CONFIRMED", result.status());
    }

    @Test
    void paymentFailsWithBadSignature() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE);
        booking.setStatus("PENDING");
        booking.setRazorpayOrderId("order_123");

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(paymentService.verifySignature("order_123", "pay_9", "sig_bad")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> bookingService.confirmPayment(5L, "test@mail.com",
                        new com.eureka.railway.dto.Dtos.PaymentRequest("pay_9", "order_123", "sig_bad")));
    }

    @Test
    void cancellingPaidPassengerRefundsThatSeatViaRazorpay() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE); // fare 150/seat
        Passenger p1 = new Passenger("Amit", 25, "Male");
        p1.setId(11L);
        Passenger p2 = new Passenger("Neha", 23, "Female");
        p2.setId(12L);
        booking.addPassenger(p1);
        booking.addPassenger(p2);
        booking.setTotalFare(300.0);
        booking.setRazorpayPaymentId("pay_123"); // booking was paid online

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentService.refund("pay_123", 150.0)).thenReturn("rfnd_1");

        BookingResponse result = bookingService.cancelPassenger(5L, 11L, "test@mail.com");

        assertEquals(150.0, result.refundedAmount()); // one seat fully refunded
        verify(paymentService).refund("pay_123", 150.0);
    }

    @Test
    void cancellingUnpaidBookingDoesNotCallRazorpay() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE);
        Passenger p1 = new Passenger("Amit", 25, "Male");
        p1.setId(11L);
        booking.addPassenger(p1);
        booking.setTotalFare(150.0);
        // no razorpayPaymentId -> nothing was paid online

        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));
        when(trainService.getByIdForUpdate(1L)).thenReturn(train);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse result = bookingService.cancel(5L, "test@mail.com");

        assertEquals("CANCELLED", result.status());
        assertEquals(0.0, result.refundedAmount());
    }

    @Test
    void cannotCancelSomeoneElsesBooking() {
        Booking booking = new Booking(user, train, SeatClass.AC3, tomorrow, FARE);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class,
                () -> bookingService.cancel(5L, "other@mail.com"));
    }
}
