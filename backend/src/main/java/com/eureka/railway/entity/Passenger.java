package com.eureka.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// @Getter/@Setter instead of @Data: @Data would also generate toString() and
// equals()/hashCode(), which on a bidirectional JPA relation
// (Passenger -> Booking -> passengers) causes infinite recursion.
@Entity
@Table(name = "passengers")
@Getter
@Setter
@NoArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // back-reference; ignored in JSON to avoid infinite recursion
    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    @JsonIgnore
    private Booking booking;

    private String name;
    private int age;
    private String gender;  // "Male", "Female", "Other"
    private String status;  // "CONFIRMED", "WAITLISTED" or "CANCELLED"

    // allotted at booking time, unique per train + class + journey date.
    // Freed automatically when the passenger is cancelled.
    private Integer seatNumber;

    // position in the queue while WAITLISTED (WL1, WL2, ...); null once confirmed
    private Integer waitlistNumber;

    public Passenger(String name, int age, String gender) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.status = "CONFIRMED";
    }
}
