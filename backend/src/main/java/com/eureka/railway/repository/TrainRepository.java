package com.eureka.railway.repository;

import com.eureka.railway.entity.Train;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {

    boolean existsByTrainNumber(String trainNumber);

   
    // trains that stop at BOTH stations, with the boarding stop earlier in the
    // route than the destination, AND that run on the journey's weekday -
    // so an intermediate journey is only found if the train runs that day
    @Query("SELECT DISTINCT t FROM Train t JOIN t.stops boarding JOIN t.stops dropping " +
           "WHERE LOWER(boarding.station) = LOWER(:source) " +
           "AND LOWER(dropping.station) = LOWER(:destination) " +
           "AND boarding.stopOrder < dropping.stopOrder " +
           "AND :day MEMBER OF t.runsOn")
    List<Train> searchByStopsOnDay(@Param("source") String source,
                                   @Param("destination") String destination,
                                   @Param("day") DayOfWeek day);

}
