package com.eureka.railway.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.SeatClass;

public interface BookingRepository extends JpaRepository<Booking,Long>{
	List<Booking>findByUserIdOrderByIdDesc(Long UserId);
	
	List<Booking>findAllByOrderByIdDesc();
	// custom JPQL query - seats occupied = confirmed passengers in that class on
    // that train for that date. Cancelled passengers are not counted, so
    // cancelling (even partially) frees seats.
    @Query("SELECT COUNT(p) FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'CONFIRMED'")
    int countBookedSeats(@Param("trainId") Long trainId,
                         @Param("seatClass") SeatClass seatClass,
                         @Param("journeyDate") LocalDate journeyDate);

    // how many people are already waiting for this train + class + date
    @Query("SELECT COUNT(p) FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'WAITLISTED'")
    int countWaitlisted(@Param("trainId") Long trainId,
                        @Param("seatClass") SeatClass seatClass,
                        @Param("journeyDate") LocalDate journeyDate);

}
