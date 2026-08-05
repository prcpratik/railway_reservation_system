package com.eureka.railway.repository;

import com.eureka.railway.entity.Booking;
import com.eureka.railway.entity.Passenger;
import com.eureka.railway.entity.SeatClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByIdDesc(Long userId);

    // all bookings, newest first - for the admin "All Bookings" view
    List<Booking> findAllByOrderByIdDesc();

    boolean existsByPnr(String pnr);

    // custom JPQL query - seats occupied = confirmed passengers in that class on
    // that train for that date. Cancelled passengers are not counted, so
    // cancelling (even partially) frees seats.
    @Query("SELECT COUNT(p) FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'CONFIRMED'")
    int countBookedSeats(@Param("trainId") Long trainId,
                         @Param("seatClass") SeatClass seatClass,
                         @Param("journeyDate") LocalDate journeyDate);

    // seat numbers already allotted for that train + class + date, so the next
    // booking can pick the lowest free ones
    @Query("SELECT p.seatNumber FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'CONFIRMED' " +
           "AND p.seatNumber IS NOT NULL")
    List<Integer> findTakenSeatNumbers(@Param("trainId") Long trainId,
                                       @Param("seatClass") SeatClass seatClass,
                                       @Param("journeyDate") LocalDate journeyDate);

    // how many people are already waiting for this train + class + date
    @Query("SELECT COUNT(p) FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'WAITLISTED'")
    int countWaitlisted(@Param("trainId") Long trainId,
                        @Param("seatClass") SeatClass seatClass,
                        @Param("journeyDate") LocalDate journeyDate);

    // the waiting queue in order, so the earliest waitlisted passenger is promoted first
    @Query("SELECT p FROM Passenger p " +
           "WHERE p.booking.train.id = :trainId AND p.booking.seatClass = :seatClass " +
           "AND p.booking.journeyDate = :journeyDate AND p.status = 'WAITLISTED' " +
           "ORDER BY p.waitlistNumber ASC")
    List<Passenger> findWaitlistQueue(@Param("trainId") Long trainId,
                                      @Param("seatClass") SeatClass seatClass,
                                      @Param("journeyDate") LocalDate journeyDate);
}
