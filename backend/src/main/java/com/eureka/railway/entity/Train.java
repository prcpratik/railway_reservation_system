package com.eureka.railway.entity;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Generated;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trains")
@Getter
@Setter
@NoArgsConstructor
public class Train {
  
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@Column(unique = true,nullable = false)
	private String trainNumber;
	
	 private String name;
	 private String source;
	 private String destination;
	 private String departureTime; // e.g. "08:30"
	 private String arrivalTime;   // e.g. "12:45"
	 
	    // days of the week the train runs on. Stored as a separate table
	    // (train_run_days) with one row per day. Defaults to all seven so trains
	    // created before this column existed carry on running daily.
	    @ElementCollection(targetClass = DayOfWeek.class, fetch = FetchType.EAGER)
	    @CollectionTable(name = "train_run_days", joinColumns = @JoinColumn(name = "train_id"))
	    @Column(name = "run_day")
	    @Enumerated(EnumType.STRING)
	    private Set<DayOfWeek> runsOn = EnumSet.allOf(DayOfWeek.class);
	    
	    
	    @OneToMany(mappedBy="train",cascade = CascadeType.ALL , orphanRemoval = true)
	    private List<TrainClass> classes = new ArrayList<>();
	    
	    // the route, in travel order. Optional: a train with no stops listed is still
	    // searchable by its source and destination.
	    @OneToMany(mappedBy = "train", cascade = CascadeType.ALL, orphanRemoval = true)
	    @OrderBy("stopOrder ASC")
	    private List<TrainStop> stops = new ArrayList<>();
	    
	    public Train(String trainNumber, String name, String source, String destination,
                String departureTime, String arrivalTime) {
       this.trainNumber = trainNumber;
       this.name = name;
       this.source = source;
       this.destination = destination;
       this.departureTime = departureTime;
       this.arrivalTime = arrivalTime;
       }
	    
	 // keeps both sides of the relation in sync
	    public void addClass(TrainClass trainClass) {
	        trainClass.setTrain(this);
	        classes.add(trainClass);
	    }
	    
	    
	 // keeps both sides in sync and numbers the stop by its position in the route
	    public void addStop(TrainStop stop) {
	        stop.setTrain(this);
	        stop.setStopOrder(stops.size() + 1);
	        stops.add(stop);
	    }
	    
	    public Optional<TrainClass> findClass(SeatClass seatClass) {
	        for (TrainClass c : classes) {
	            if (c.getSeatClass() == seatClass) {
	                return Optional.of(c); // Found matching class, wrap and return immediately
	            }
	        }
	        return Optional.empty(); // Checked all classes and found no match
	    }
	   
	    public Optional<TrainStop> findStop(String station) {
	        if (station == null || station.isBlank()) {
	            return Optional.empty();
	        }

	        String cleanStation = station.trim();

	        for (TrainStop s : stops) {
	            if (s.getStation() != null && s.getStation().equalsIgnoreCase(cleanStation)) {
	                return Optional.of(s); // Found match
	            }
	        }

	        return Optional.empty(); // No match found
	    }
	    
	    public double fareShare(TrainStop from, TrainStop to) {
	        if (stops.isEmpty() || from == null || to == null) {
	            return 1.0;
	        }
	        TrainStop first = stops.get(0);
	        TrainStop last = stops.get(stops.size() - 1);
	        int totalKm = last.getDistanceFromSource() - first.getDistanceFromSource();
	        if (totalKm > 0) {
	            int segmentKm = to.getDistanceFromSource() - from.getDistanceFromSource();
	            if (segmentKm > 0) {
	                return (double) segmentKm / totalKm;
	            }
	        }
	        int totalStops = last.getStopOrder() - first.getStopOrder();
	        return totalStops <= 0 ? 1.0 : (double) (to.getStopOrder() - from.getStopOrder()) / totalStops;
	    }
	    
	    
	   
	    
	    
	    
	
}
