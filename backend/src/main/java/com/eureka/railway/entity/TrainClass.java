package com.eureka.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One travel class offered on one train, with its own capacity and fare.
// A train has as many of these rows as the classes it actually runs.
@Entity
@Table(name = "train_classes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"train_id", "seat_class"}))
@Getter
@Setter
@NoArgsConstructor
public class TrainClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "train_id", nullable = false)
    @JsonIgnore
    private Train train;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false)
    private SeatClass seatClass;

    private int totalSeats;
    private double fare;

    // not stored - computed per journey date by the service
    @Transient
    private int availableSeats;

    // fare for the searched part of the route; equals fare for a full journey
    @Transient
    private double segmentFare;

    // how many people are currently waitlisted for this class on that date
    @Transient
    private int waitlistCount;

    public TrainClass(SeatClass seatClass, int totalSeats, double fare) {
        this.seatClass = seatClass;
        this.totalSeats = totalSeats;
        this.fare = fare;
    }

    // display name for the UI ("3AC"); read-only so it is never expected on input
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getSeatClassLabel() {
        return seatClass == null ? null : seatClass.getLabel();
    }
}
