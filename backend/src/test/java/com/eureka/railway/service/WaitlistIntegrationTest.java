package com.eureka.railway.service;

import com.eureka.railway.dto.Dtos.BookingResponse;
import com.eureka.railway.dto.Dtos.PassengerRequest;
import com.eureka.railway.dto.Dtos.PassengerResponse;
import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.User;
import com.eureka.railway.repository.TrainRepository;
import com.eureka.railway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// End-to-end waitlist behaviour against a real (in-memory H2) database:
// booking beyond capacity queues, and cancelling promotes the first in line.
@SpringBootTest
@ActiveProfiles("test")
class WaitlistIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private UserRepository userRepository;

    private final LocalDate journeyDate = LocalDate.now().plusDays(3);
    private Train train;
    private String email;

    @BeforeEach
    void setUp() {
        // unique per test run so repeated runs do not clash
        String suffix = String.valueOf(System.nanoTime());
        email = "wl" + suffix + "@test.com";
        userRepository.save(new User("WL Tester", email, "x", "USER"));

        Train t = new Train("WL" + suffix.substring(suffix.length() - 4),
                "Waitlist Express", "Pune", "Nagpur", "06:00", "18:00");
        t.addClass(new TrainClass(SeatClass.AC2, 2, 500)); // only 2 seats
        train = trainRepository.save(t);
    }

    private BookingResponse book(String passengerName, boolean allowWaitlist) {
        return bookingService.book(email, train.getId(), SeatClass.AC2, journeyDate,
                List.of(new PassengerRequest(passengerName, 30, "Male")), allowWaitlist, null, null);
    }

    @Test
    void bookingBeyondCapacityJoinsTheWaitlistAndIsPromotedOnCancellation() {
        BookingResponse first = book("First", false);
        BookingResponse second = book("Second", false);
        assertEquals("CONFIRMED", first.status());
        assertEquals("CONFIRMED", second.status());

        // the class is now full - without asking for the waitlist the booking is refused
        assertThrows(IllegalArgumentException.class, () -> book("Third", false));

        // asking for it puts the passenger in the queue at WL1
        BookingResponse third = book("Third", true);
        assertEquals("WAITLISTED", third.status());
        assertEquals(1, third.passengers().get(0).waitlistNumber());
        assertNull(third.passengers().get(0).seatNumber());

        BookingResponse fourth = book("Fourth", true);
        assertEquals(2, fourth.passengers().get(0).waitlistNumber());

        // cancelling a confirmed ticket frees a seat -> WL1 is promoted, WL2 moves up
        bookingService.cancel(first.id(), email);

        BookingResponse promoted = findBooking(third.id());
        PassengerResponse promotedPassenger = promoted.passengers().get(0);
        assertEquals("CONFIRMED", promotedPassenger.status(), "WL1 should have been promoted");
        assertNotNull(promotedPassenger.seatNumber(), "a promoted passenger must get a seat");
        assertNull(promotedPassenger.waitlistNumber());
        assertEquals("CONFIRMED", promoted.status());

        BookingResponse stillWaiting = findBooking(fourth.id());
        assertEquals("WAITLISTED", stillWaiting.status());
        assertEquals(1, stillWaiting.passengers().get(0).waitlistNumber(), "WL2 should move up to WL1");
    }

    private BookingResponse findBooking(Long id) {
        return bookingService.myBookings(email).stream()
                .filter(b -> b.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
