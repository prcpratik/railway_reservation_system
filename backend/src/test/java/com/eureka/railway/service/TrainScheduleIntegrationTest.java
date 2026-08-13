package com.eureka.railway.service;

import com.eureka.railway.dto.Dtos.PassengerRequest;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// A train's days-of-week must filter both search results and bookings, so a
// train that runs Mon/Wed/Fri does not show up (or accept bookings) on Tuesday.
@SpringBootTest
@ActiveProfiles("test")
class TrainScheduleIntegrationTest {

    @Autowired
    private TrainService trainService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private UserRepository userRepository;

    private String number;
    private String email;

    @BeforeEach
    void setUp() {
        long unique = System.nanoTime();
        number = "SCH" + (unique % 100000);
        email = "sch" + unique + "@test.com";
        userRepository.save(new User("Schedule Tester", email, "x", "USER"));

        Train t = new Train(number, "MonWedFri Express", "Pune", "Mumbai", "07:00", "10:00");
        t.addClass(new TrainClass(SeatClass.AC3, 20, 300));
        t.setRunsOn(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        trainRepository.save(t);
    }

    private boolean found(LocalDate date) {
        return trainService.search("Pune", "Mumbai", date).stream()
                .anyMatch(t -> number.equals(t.getTrainNumber()));
    }

    private LocalDate next(DayOfWeek day) {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != day) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    void searchOnlyShowsTrainForItsRunDays() {
        assertTrue(found(next(DayOfWeek.MONDAY)), "should show on a Monday");
        assertTrue(found(next(DayOfWeek.WEDNESDAY)), "should show on a Wednesday");
        assertTrue(found(next(DayOfWeek.FRIDAY)), "should show on a Friday");
        assertFalse(found(next(DayOfWeek.TUESDAY)), "must not show on a Tuesday");
        assertFalse(found(next(DayOfWeek.SATURDAY)), "must not show on a Saturday");
    }

    @Test
    void bookingIsRejectedOnANonRunDay() {
        Long trainId = trainRepository.findAll().stream()
                .filter(t -> number.equals(t.getTrainNumber()))
                .findFirst().orElseThrow().getId();
        LocalDate tuesday = next(DayOfWeek.TUESDAY);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> bookingService.book(email, trainId, SeatClass.AC3, tuesday,
                        List.of(new PassengerRequest("Nope", 30, "Male")), false, null, null));
        assertTrue(e.getMessage().toLowerCase().contains("does not run"),
                "message should explain the train doesn't run that day: " + e.getMessage());
    }
}
