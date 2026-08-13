package com.eureka.railway.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pnr; // 10-digit ticket number shown to the user

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "train_id", nullable = false)
    private Train train;

    // one ticket -> many passengers; saving/deleting the booking cascades to them
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Passenger> passengers = new ArrayList<>();

    // which class was booked; fare and capacity come from that class
    @Enumerated(EnumType.STRING)
    private SeatClass seatClass;

    // the part of the route actually booked. Null on older bookings, which are
    // treated as the train's full journey.
    private String fromStation;
    private String toStation;
    private int fromStopOrder;
    private int toStopOrder;

    private LocalDate journeyDate;
    private double farePerSeat; // fare locked at booking time
    private double totalFare;   // farePerSeat x confirmed passengers
    private String status;      // "PENDING" (awaiting payment), "CONFIRMED", "PARTIALLY_CANCELLED" or "CANCELLED"
    private LocalDateTime bookedAt;

    // filled only when Razorpay payment is enabled
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private double refundedAmount;

    public Booking(User user, Train train, SeatClass seatClass, LocalDate journeyDate, double farePerSeat) {
        this.user = user;
        this.train = train;
        this.seatClass = seatClass;
        this.journeyDate = journeyDate;
        this.farePerSeat = farePerSeat;
        this.status = "CONFIRMED";
        this.bookedAt = LocalDateTime.now();
    }

    // keeps both sides of the relation in sync
    public void addPassenger(Passenger passenger) {
        passenger.setBooking(this);
        passengers.add(passenger);
    }
}
