
package com.eureka.railway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One station on a train's route. stopOrder gives the position along the route,
// so a journey is valid only when the boarding stop comes before the getting-off
// stop. Seats are still booked for the whole run (see PROJECT_NOTES).
@Entity
@Table(name = "train_stops",
       uniqueConstraints = @UniqueConstraint(columnNames = {"train_id", "stop_order"}))
@Getter
@Setter
@NoArgsConstructor
public class TrainStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "train_id", nullable = false)
    @JsonIgnore
    private Train train;

    @Column(nullable = false)
    private String station;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    private String arrivalTime;   // blank at the first stop
    private String departureTime; // blank at the last stop

    // kilometres from the start of the route; used to price part-journeys.
    // nullable=false + a default keeps rows created before this column existed
    // from breaking the mapping (a primitive int cannot hold NULL).
    @Column(name = "distance_from_source", nullable = false, columnDefinition = "int default 0")
    private int distanceFromSource;

    public TrainStop(String station, String arrivalTime, String departureTime) {
        this.station = station;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }

    public TrainStop(String station, String arrivalTime, String departureTime, int distanceFromSource) {
        this(station, arrivalTime, departureTime);
        this.distanceFromSource = distanceFromSource;
    }
}
