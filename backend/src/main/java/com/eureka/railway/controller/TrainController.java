package com.eureka.railway.controller;

import com.eureka.railway.entity.Train;
import com.eureka.railway.service.TrainService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trains")
public class TrainController {

    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    // GET /api/trains?source=Pune&destination=Mumbai&date=2026-08-01
    @GetMapping
    public List<Train> search(@RequestParam(required = false) String source,
                              @RequestParam(required = false) String destination,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return trainService.search(source, destination, date);
    }

    // admin-only endpoints (see SecurityConfig)
    @PostMapping
    public ResponseEntity<Train> add(@RequestBody Train train) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainService.add(train));
    }

    @PutMapping("/{id}")
    public Train update(@PathVariable Long id, @RequestBody Train train) {
        return trainService.update(id, train);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        trainService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
