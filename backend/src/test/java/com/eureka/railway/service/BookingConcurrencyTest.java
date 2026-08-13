package com.eureka.railway.service;

import com.eureka.railway.dto.Dtos.PassengerRequest;
import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.User;
import com.eureka.railway.repository.BookingRepository;
import com.eureka.railway.repository.TrainRepository;
import com.eureka.railway.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Integration test on a real (in-memory H2) database that proves the pessimistic
// row lock stops the train from being oversold when many people book at once.
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void doesNotOversellTheLastSeatsUnderConcurrentBooking() throws InterruptedException {
        // a train with only 5 seats, and 12 people all trying to grab one at once
        int totalSeats = 5;
        int threads = 12;
        LocalDate journeyDate = LocalDate.now().plusDays(1);

        User user = userRepository.save(new User("Race Tester", "race@test.com", "x", "USER"));
        Train raceTrain = new Train("99999", "Race Express", "Pune", "Mumbai", "08:00", "11:00");
        raceTrain.addClass(new TrainClass(SeatClass.AC3, totalSeats, 100));
        Train train = trainRepository.save(raceTrain);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1); // released so all threads fire together
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    bookingService.book(user.getEmail(), train.getId(), SeatClass.AC3, journeyDate,
                            List.of(new PassengerRequest("P" + Thread.currentThread().getId(), 30, "Male")), false, null, null);
                    success.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    rejected.incrementAndGet(); // "Only X seats available" - expected for the losers
                } catch (Exception e) {
                    // any other exception is a real failure
                } finally {
                    done.countDown();
                }
            });
        }

        startGun.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "bookings did not finish in time");
        pool.shutdown();

        // exactly 5 seats sold, 7 rejected, and never more than the train holds
        int booked = bookingRepository.countBookedSeats(train.getId(), SeatClass.AC3, journeyDate);
        assertEquals(totalSeats, success.get(), "should confirm exactly the available seats");
        assertEquals(threads - totalSeats, rejected.get(), "the rest must be rejected");
        assertTrue(booked <= totalSeats, "train must never be oversold");
    }
}
