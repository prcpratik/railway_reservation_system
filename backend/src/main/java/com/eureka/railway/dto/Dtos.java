package com.eureka.railway.dto;

import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.Passenger;
import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainStop;
import com.eureka.railway.entity.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// small request/response records used by the controllers
public class Dtos {

    public record RegisterRequest(String name, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record AuthResponse(String token, String name, String email, String role) {
    }

    public record ProfileResponse(String name, String email, String role, String phone) {
        
        public static ProfileResponse from(User u) {
            return new ProfileResponse(u.getName(), u.getEmail(), u.getRole(), u.getPhone());
        }
    }

    public record UpdateProfileRequest(String name, String phone) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record ForgotPasswordRequest(String email) {
    }

    public record ResetPasswordRequest(String token, String newPassword) {
    }

    public record PassengerRequest(String name, Integer age, String gender) {
    }

    // fromStation/toStation are optional: leave them null to book the train's whole
    // route, or give two stations from its route (in travel order) to book that part
    public record BookingRequest(Long trainId, SeatClass seatClass, LocalDate journeyDate,
                                 List<PassengerRequest> passengers, boolean allowWaitlist,
                                 String fromStation, String toStation) {
    }

    public record PassengerResponse(Long id, String name, int age, String gender, String status,
                                    Integer seatNumber, Integer waitlistNumber) {

        public static PassengerResponse from(Passenger p) {
            return new PassengerResponse(p.getId(), p.getName(), p.getAge(), p.getGender(), p.getStatus(),
                    p.getSeatNumber(), p.getWaitlistNumber());
        }
    }

    // sent by the frontend after the Razorpay checkout succeeds
    public record PaymentRequest(String razorpayPaymentId, String razorpayOrderId, String razorpaySignature) {
    }

    // tells the frontend whether payment is on and which public key to use
    public record PaymentConfig(boolean enabled, String keyId) {
    }

    // what the frontend sees for a booking - no nested User/Train entities.
    // userName/userEmail identify who booked (used by the admin "All Bookings" view).
    // source/destination are the stations actually booked (which may be a part of
    // the train's route); trainSource/trainDestination are the train's end points.
    public record BookingResponse(Long id, String pnr, String userName, String userEmail,
                                  String trainNumber, String trainName,
                                  String source, String destination,
                                  String trainSource, String trainDestination,
                                  String departureTime, String arrivalTime,
                                  SeatClass seatClass, String seatClassLabel,
                                  LocalDate journeyDate, String status,
                                  double farePerSeat, double totalFare,
                                  double refundedAmount,
                                  String razorpayOrderId,
                                  List<PassengerResponse> passengers) {

        public static BookingResponse from(Booking b) {
            Train train = b.getTrain();
            // older bookings have no segment recorded: treat them as the full journey
            String from = b.getFromStation() != null ? b.getFromStation() : train.getSource();
            String to = b.getToStation() != null ? b.getToStation() : train.getDestination();
            String departs = train.getDepartureTime();
            String arrives = train.getArrivalTime();
            for (TrainStop stop : train.getStops()) {
                if (stop.getStopOrder() == b.getFromStopOrder() && notBlank(stop.getDepartureTime())) {
                    departs = stop.getDepartureTime();
                }
                if (stop.getStopOrder() == b.getToStopOrder() && notBlank(stop.getArrivalTime())) {
                    arrives = stop.getArrivalTime();
                }
            }
            
            // 1. Created an empty list for the DTOs to store dtos
            List<PassengerResponse> passengerList = new ArrayList<>();

            // 2. Loop through each Passenger entity in the booking
            for (Passenger passenger : b.getPassengers()) {
                                // Convert and add to list
                    PassengerResponse dto = PassengerResponse.from(passenger);
                    passengerList.add(dto);
                        }
            
            return new BookingResponse(
                    b.getId(),
                    b.getPnr(),
                    b.getUser().getName(),
                    b.getUser().getEmail(),
                    train.getTrainNumber(),
                    train.getName(),
                    from,
                    to,
                    train.getSource(),
                    train.getDestination(),
                    departs,
                    arrives,
                    b.getSeatClass(),
                    b.getSeatClass() == null ? null : b.getSeatClass().getLabel(),
                    b.getJourneyDate(),
                    b.getStatus(),
                    b.getFarePerSeat(),
                    b.getTotalFare(),
                    b.getRefundedAmount(),
                    b.getRazorpayOrderId(),
                    passengerList);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
