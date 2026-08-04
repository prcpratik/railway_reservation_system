package com.eureka.railway.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.ManyToAny;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	private String pnr ; // 10-digit ticket number shown to the user 
	
	//One user can do multiple bookings
	@ManyToOne
	@JoinColumn(name = "user_id" , nullable = false)
	private User user;
	
	//One train can have multiple bookings
	@ManyToOne
	@JoinColumn(name = "train_id" , nullable = false)
	private Train train;
	
	//One booking can have multiple passengers
	//On deleting the booking also deletes the related passengers
	@OneToMany(mappedBy = "booking" , cascade = CascadeType.ALL , orphanRemoval = true)
	private List<Passenger> passengers = new ArrayList<>();
	
	//type of the seat class , fare and capacity
	@Enumerated(EnumType.STRING)
	private SeatClass seatClass;
	
	// the part of the route actually booked. Null on older bookings, which are
    // treated as the train's full journey.
	private String fromStation;
	private String toStation;
	private int fromStopOrder;
	private int toStopOrder;
	
	private LocalDate journeyDate;
	private double farePerSeat; // fare Locked at booking time
	private double totalFare;	// farePerSeat * confirmed passengers
	private String status;		// "PENDING"(awaited payment) , "CONFIRMED" , "PARTIALLY CANCELLED" or "CANCELLED"
	private LocalDateTime bookedAt;
	
	//filled only when razorpay payment is enabled
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
