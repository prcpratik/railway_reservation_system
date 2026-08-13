package com.eureka.railway.config;

import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.TrainStop;
import com.eureka.railway.entity.User;
import com.eureka.railway.repository.TrainRepository;
import com.eureka.railway.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

import static java.time.DayOfWeek.*;

// inserts an admin user and some sample trains on first run
@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedData(UserRepository userRepository, TrainRepository trainRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(new User("Admin", "admin@railway.com",
                        passwordEncoder.encode("admin123"), "ADMIN"));
            }
            if (trainRepository.count() == 0) {
                // Every train has a distinct schedule so the days-of-week feature is
                // visibly working, and every weekday still has 3 or more trains running.

                // Pragati Express — daily commuter, no Sunday
                trainRepository.save(runsOn(withRoute(
                        train("12126", "Pragati Express", "Pune", "Mumbai", "07:15", "10:45",
                                new TrainClass(SeatClass.AC2, 40, 420),
                                new TrainClass(SeatClass.AC3, 60, 310),
                                new TrainClass(SeatClass.GENERAL, 120, 155)),
                        stop("Pune", "", "07:15", 0),
                        stop("Lonavala", "08:05", "08:07", 64),
                        stop("Karjat", "08:45", "08:47", 100),
                        stop("Kalyan", "09:35", "09:37", 138),
                        stop("Mumbai", "10:45", "", 192)),
                        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY));

                // Sinhagad Express — return commuter, no Sunday
                trainRepository.save(runsOn(withRoute(
                        train("11010", "Sinhagad Express", "Mumbai", "Pune", "14:30", "18:20",
                                new TrainClass(SeatClass.AC3, 50, 290),
                                new TrainClass(SeatClass.SLEEPER, 80, 180),
                                new TrainClass(SeatClass.GENERAL, 140, 140)),
                        stop("Mumbai", "", "14:30", 0),
                        stop("Kalyan", "15:15", "15:17", 54),
                        stop("Karjat", "16:00", "16:02", 92),
                        stop("Lonavala", "16:50", "16:52", 128),
                        stop("Pune", "18:20", "", 192)),
                        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY));

                // Rajdhani Express — bi-directional long-distance, alternate days
                trainRepository.save(runsOn(withRoute(
                        train("12951", "Rajdhani Express", "Mumbai", "Delhi", "17:00", "08:30",
                                new TrainClass(SeatClass.AC1, 24, 5200),
                                new TrainClass(SeatClass.AC2, 48, 3100),
                                new TrainClass(SeatClass.AC3, 72, 2100)),
                        stop("Mumbai", "", "17:00", 0),
                        stop("Surat", "20:05", "20:10", 263),
                        stop("Vadodara", "22:00", "22:10", 392),
                        stop("Ratlam", "01:15", "01:20", 660),
                        stop("Kota", "03:35", "03:45", 1052),
                        stop("Delhi", "08:30", "", 1384)),
                        MONDAY, WEDNESDAY, FRIDAY, SUNDAY));

                // Karnataka Express — long-distance, on the days Rajdhani doesn't run + weekends
                trainRepository.save(runsOn(withRoute(
                        train("12627", "Karnataka Express", "Delhi", "Bengaluru", "21:15", "13:40",
                                new TrainClass(SeatClass.AC2, 46, 2850),
                                new TrainClass(SeatClass.AC3, 64, 1850),
                                new TrainClass(SeatClass.SLEEPER, 90, 720),
                                new TrainClass(SeatClass.GENERAL, 110, 410)),
                        stop("Delhi", "", "21:15", 0),
                        stop("Agra", "23:40", "23:50", 195),
                        stop("Gwalior", "01:10", "01:15", 305),
                        stop("Bhopal", "06:10", "06:20", 702),
                        stop("Nagpur", "12:30", "12:45", 1092),
                        stop("Bengaluru", "13:40", "", 2444)),
                        TUESDAY, THURSDAY, SATURDAY, SUNDAY));

                // Deccan Queen — historic day commuter, weekdays only
                trainRepository.save(runsOn(withRoute(
                        train("12123", "Deccan Queen", "Pune", "Mumbai", "07:15", "10:25",
                                new TrainClass(SeatClass.AC2, 36, 400),
                                new TrainClass(SeatClass.GENERAL, 110, 150)),
                        stop("Pune", "", "07:15", 0),
                        stop("Lonavala", "08:00", "08:02", 64),
                        stop("Karjat", "08:40", "08:42", 100),
                        stop("Mumbai", "10:25", "", 192)),
                        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY));

                // Rani Chennamma Express — three days a week
                trainRepository.save(runsOn(withRoute(
                        train("16590", "Rani Chennamma Express", "Bengaluru", "Kolhapur", "20:30", "11:05",
                                new TrainClass(SeatClass.AC2, 30, 1650),
                                new TrainClass(SeatClass.AC3, 54, 1120),
                                new TrainClass(SeatClass.SLEEPER, 90, 720)),
                        stop("Bengaluru", "", "20:30", 0),
                        stop("Hubballi", "04:15", "04:30", 468),
                        stop("Belagavi", "07:20", "07:30", 613),
                        stop("Kolhapur", "11:05", "", 762)),
                        WEDNESDAY, FRIDAY, SUNDAY));
            }
        };
    }

    private Train train(String number, String name, String source, String destination,
                        String departure, String arrival, TrainClass... classes) {
        Train train = new Train(number, name, source, destination, departure, arrival);
        for (TrainClass trainClass : classes) {
            train.addClass(trainClass);
        }
        return train;
    }

    // adds the intermediate route in travel order
    private Train withRoute(Train train, TrainStop... stops) {
        for (TrainStop stop : stops) {
            train.addStop(stop);
        }
        return train;
    }

    // arrival is blank at the first stop, departure blank at the last;
    // km is the distance from the start of the route, used to price part-journeys
    private TrainStop stop(String station, String arrival, String departure, int km) {
        return new TrainStop(station, arrival, departure, km);
    }

    // narrow a train's days-of-week (default is all seven)
    private Train runsOn(Train train, DayOfWeek... days) {
        Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : days) {
            set.add(day);
        }
        train.setRunsOn(set);
        return train;
    }
}
