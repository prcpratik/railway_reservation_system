package com.eureka.railway.service;

import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.TrainStop;
import com.eureka.railway.entity.User;
import com.eureka.railway.dto.Dtos.BookingResponse;
import com.eureka.railway.dto.Dtos.PassengerRequest;
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

// Searching by intermediate stations: a train must be found for any pair of its
// stops in travel order, and must NOT be found for a pair in the wrong direction.
@SpringBootTest
@ActiveProfiles("test")
class TrainRouteIntegrationTest {

    @Autowired
    private TrainService trainService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    private final LocalDate journeyDate = LocalDate.now().plusDays(2);
    private String number;
    private String email;

    @BeforeEach
    void setUp() {
        long unique = System.nanoTime();
        number = "RT" + (unique % 100000);
        email = "route" + unique + "@test.com";
        Train t = new Train(number, "Route Express", "Delhi", "Chennai", "10:00", "20:00");
        t.addClass(new TrainClass(SeatClass.SLEEPER, 40, 900));
        t.addStop(new TrainStop("Delhi", "", "10:00", 0));
        t.addStop(new TrainStop("Bhopal", "16:00", "16:10", 700));
        t.addStop(new TrainStop("Nagpur", "20:30", "20:40", 1100));
        t.addStop(new TrainStop("Chennai", "20:00", "", 2200));
        trainRepository.save(t);
    }

    private boolean found(String from, String to) {
        return trainService.search(from, to, journeyDate).stream()
                .anyMatch(t -> number.equals(t.getTrainNumber()));
    }

    @Test
    void findsTrainForAnyStopPairInTravelOrder() {
        assertTrue(found("Delhi", "Chennai"), "end to end");
        assertTrue(found("Bhopal", "Nagpur"), "intermediate to intermediate");
        assertTrue(found("Delhi", "Nagpur"), "start to intermediate");
        assertTrue(found("Bhopal", "Chennai"), "intermediate to end");
        assertTrue(found("bhopal", "NAGPUR"), "station match is case-insensitive");
    }

    @Test
    void doesNotFindTrainForReversedOrUnservedStops() {
        assertFalse(found("Nagpur", "Bhopal"), "wrong direction along the route");
        assertFalse(found("Chennai", "Delhi"), "reverse of the whole route");
        assertFalse(found("Bhopal", "Jaipur"), "station not on this route");
    }

    @Test
    void partJourneyIsChargedInProportionToDistance() {
        // route distances: Delhi 0, Bhopal 700, Nagpur 1100, Chennai 2200 km
        // Sleeper full-route fare is 900 for 2200 km
        userRepository.save(new User("Fare Tester", email, "x", "USER"));
        Long trainId = trainRepository.findAll().stream()
                .filter(t -> number.equals(t.getTrainNumber()))
                .findFirst().orElseThrow().getId();

        // Bhopal -> Nagpur is 400 of 2200 km => 900 * 400/2200 = 164 (rounded)
        BookingResponse part = bookingService.book(email, trainId, SeatClass.SLEEPER, journeyDate,
                List.of(new PassengerRequest("Segment", 30, "Male")), false, "Bhopal", "Nagpur");
        assertEquals("Bhopal", part.source());
        assertEquals("Nagpur", part.destination());
        assertEquals(164.0, part.farePerSeat());
        // the boarding/arrival times come from those two stops, not the whole train
        assertEquals("16:10", part.departureTime());
        assertEquals("20:30", part.arrivalTime());

        // the whole route still costs the full fare
        BookingResponse full = bookingService.book(email, trainId, SeatClass.SLEEPER, journeyDate,
                List.of(new PassengerRequest("Full", 30, "Male")), false, null, null);
        assertEquals(900.0, full.farePerSeat());
        assertEquals("Delhi", full.source());
        assertEquals("Chennai", full.destination());
    }

    @Test
    void bookingRejectsStationsNotOnTheRouteOrInTheWrongOrder() {
        userRepository.save(new User("Bad Segment", email, "x", "USER"));
        Long trainId = trainRepository.findAll().stream()
                .filter(t -> number.equals(t.getTrainNumber()))
                .findFirst().orElseThrow().getId();
        List<PassengerRequest> one = List.of(new PassengerRequest("X", 30, "Male"));

        assertThrows(IllegalArgumentException.class, () -> bookingService.book(email, trainId,
                SeatClass.SLEEPER, journeyDate, one, false, "Nagpur", "Bhopal"), "wrong direction");
        assertThrows(IllegalArgumentException.class, () -> bookingService.book(email, trainId,
                SeatClass.SLEEPER, journeyDate, one, false, "Jaipur", "Nagpur"), "station not on route");
    }

    @Test
    void routeIsReturnedInTravelOrder() {
        Train train = trainService.search("Delhi", "Chennai", journeyDate).stream()
                .filter(t -> number.equals(t.getTrainNumber()))
                .findFirst().orElseThrow();
        List<String> stations = train.getStops().stream().map(TrainStop::getStation).toList();
        assertEquals(List.of("Delhi", "Bhopal", "Nagpur", "Chennai"), stations);
    }
}
