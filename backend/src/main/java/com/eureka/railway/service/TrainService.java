package com.eureka.railway.service;

import com.eureka.railway.entity.SeatClass;
import com.eureka.railway.entity.Train;
import com.eureka.railway.entity.TrainClass;
import com.eureka.railway.entity.TrainStop;
import com.eureka.railway.repository.BookingRepository;
import com.eureka.railway.repository.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TrainService {

    private final TrainRepository trainRepository;
    private final BookingRepository bookingRepository;

    public TrainService(TrainRepository trainRepository, BookingRepository bookingRepository) {
        this.trainRepository = trainRepository;
        this.bookingRepository = bookingRepository;
    }

    // search trains and fill availableSeats on every class for the given journey date.
    // Trains that do not run on that date's weekday are excluded.
    @Transactional(readOnly = true)
    public List<Train> search(String source, String destination, LocalDate journeyDate) {
        LocalDate date = journeyDate == null ? LocalDate.now() : journeyDate;
        List<Train> trains;
        if (source != null && !source.isBlank() && destination != null && !destination.isBlank()) {
            // a train matches if these are its end points, OR if both appear on its
            // route in travel order (so intermediate journeys are found too)
            Set<Train> matches = new LinkedHashSet<>(
                    trainRepository.searchByStopsOnDay(source.trim(), destination.trim(), date.getDayOfWeek()));
            matches.addAll(trainRepository.findByEndpointsOnDay(
                    source.trim(), destination.trim(), date.getDayOfWeek()));
            trains = new ArrayList<>(matches);
        } else {
            // no route filter: still hide trains that do not run that weekday
            trains = trainRepository.findAll().stream()
                    .filter(t -> t.getRunsOn().contains(date.getDayOfWeek()))
                    .toList();
        }
        for (Train train : trains) {
            train.getStops().size(); // initialise the route before the session closes
            fillAvailability(train, journeyDate);
            fillSegmentFare(train, source, destination);
        }
        return trains;
    }

    // prices the searched part of the route, so search never advertises the
    // full-journey fare for a short hop
    private void fillSegmentFare(Train train, String source, String destination) {
        double share = 1.0;
        var from = train.findStop(source);
        var to = train.findStop(destination);
        if (from.isPresent() && to.isPresent() && from.get().getStopOrder() < to.get().getStopOrder()) {
            share = train.fareShare(from.get(), to.get());
        }
        for (TrainClass trainClass : train.getClasses()) {
            trainClass.setSegmentFare(Math.round(trainClass.getFare() * share));
        }
    }

    public void fillAvailability(Train train, LocalDate journeyDate) {
        for (TrainClass trainClass : train.getClasses()) {
            int booked = bookingRepository.countBookedSeats(train.getId(), trainClass.getSeatClass(), journeyDate);
            trainClass.setAvailableSeats(trainClass.getTotalSeats() - booked);
            trainClass.setWaitlistCount(
                    bookingRepository.countWaitlisted(train.getId(), trainClass.getSeatClass(), journeyDate));
        }
    }

    @Transactional(readOnly = true)
    public Train getById(Long id) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found"));
        train.getClasses().size(); // initialise before the session closes
        train.getStops().size();
        return train;
    }

    // same as getById but takes a write lock on the row; must be called inside a
    // transaction (e.g. from BookingService.book) so the lock is held until commit
    public Train getByIdForUpdate(Long id) {
        return trainRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Train not found"));
    }

    @Transactional
    public Train add(Train train) {
        if (trainRepository.existsByTrainNumber(train.getTrainNumber())) {
            throw new IllegalArgumentException("Train number already exists");
        }
        validateClasses(train.getClasses());
        validateRunsOn(train);
        // Jackson filled the lists but not the back-references, so set them here
        train.getClasses().forEach(c -> c.setTrain(train));
        List<TrainStop> stops = new ArrayList<>(train.getStops());
        train.getStops().clear();
        applyStops(train, stops);
        return trainRepository.save(train);
    }

    @Transactional
    public Train update(Long id, Train updated) {
        Train train = getById(id);
        validateClasses(updated.getClasses());
        validateRunsOn(updated);
        train.setRunsOn(updated.getRunsOn());

        train.setTrainNumber(updated.getTrainNumber());
        train.setName(updated.getName());
        train.setSource(updated.getSource());
        train.setDestination(updated.getDestination());
        train.setDepartureTime(updated.getDepartureTime());
        train.setArrivalTime(updated.getArrivalTime());

        // replace the classes and stops: clear and flush first so orphanRemoval
        // deletes the old rows before the new ones are inserted (unique keys clash)
        train.getClasses().clear();
        train.getStops().clear();
        trainRepository.saveAndFlush(train);
        for (TrainClass c : updated.getClasses()) {
            train.addClass(new TrainClass(c.getSeatClass(), c.getTotalSeats(), c.getFare()));
        }
        applyStops(train, updated.getStops());
        return trainRepository.save(train);
    }

    // validates the route and, when one is given, derives the train's end points
    // and timings from the first and last stop so the two can never disagree
    private void applyStops(Train train, List<TrainStop> stops) {
        if (stops == null || stops.isEmpty()) {
            return; // route is optional
        }
        if (stops.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two stops");
        }
        Set<String> seen = new HashSet<>();
        int previousKm = -1;
        for (TrainStop stop : stops) {
            if (stop.getStation() == null || stop.getStation().isBlank()) {
                throw new IllegalArgumentException("Every stop needs a station name");
            }
            if (!seen.add(stop.getStation().trim().toLowerCase())) {
                throw new IllegalArgumentException("Station " + stop.getStation() + " is listed twice");
            }
            if (stop.getDistanceFromSource() < 0) {
                throw new IllegalArgumentException("Distance cannot be negative for " + stop.getStation());
            }
            // distance must grow along the route, otherwise part-fares make no sense
            if (stop.getDistanceFromSource() < previousKm) {
                throw new IllegalArgumentException("Distance at " + stop.getStation()
                        + " is less than at the previous stop");
            }
            previousKm = stop.getDistanceFromSource();
        }
        for (TrainStop stop : stops) {
            train.addStop(new TrainStop(stop.getStation().trim(),
                    stop.getArrivalTime(), stop.getDepartureTime(), stop.getDistanceFromSource()));
        }
        TrainStop first = train.getStops().get(0);
        TrainStop last = train.getStops().get(train.getStops().size() - 1);
        train.setSource(first.getStation());
        train.setDestination(last.getStation());
        if (first.getDepartureTime() != null && !first.getDepartureTime().isBlank()) {
            train.setDepartureTime(first.getDepartureTime());
        }
        if (last.getArrivalTime() != null && !last.getArrivalTime().isBlank()) {
            train.setArrivalTime(last.getArrivalTime());
        }
    }

    public void delete(Long id) {
        trainRepository.deleteById(id);
    }

    // a train that runs on no day is useless (and would never appear in search).
    // Null is allowed and treated as "runs every day" so old data still works.
    private void validateRunsOn(Train train) {
        if (train.getRunsOn() != null && train.getRunsOn().isEmpty()) {
            throw new IllegalArgumentException("Pick at least one day the train runs on");
        }
    }

    private void validateClasses(List<TrainClass> classes) {
        if (classes == null || classes.isEmpty()) {
            throw new IllegalArgumentException("Add at least one travel class");
        }
        Set<SeatClass> seen = EnumSet.noneOf(SeatClass.class);
        for (TrainClass c : classes) {
            if (c.getSeatClass() == null) {
                throw new IllegalArgumentException("Select a travel class for every row");
            }
            if (!seen.add(c.getSeatClass())) {
                throw new IllegalArgumentException(c.getSeatClass().getLabel() + " is added twice");
            }
            if (c.getTotalSeats() < 1) {
                throw new IllegalArgumentException("Seats must be at least 1 for " + c.getSeatClass().getLabel());
            }
            if (c.getFare() < 0) {
                throw new IllegalArgumentException("Fare cannot be negative for " + c.getSeatClass().getLabel());
            }
        }
    }
}
