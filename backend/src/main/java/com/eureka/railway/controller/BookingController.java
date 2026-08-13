package com.eureka.railway.controller;

import com.eureka.railway.dto.Dtos.BookingRequest;
import com.eureka.railway.dto.Dtos.BookingResponse;
import com.eureka.railway.dto.Dtos.PaymentRequest;
import com.eureka.railway.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // Authentication is filled by JwtFilter; getName() is the user's email
    @PostMapping
    public ResponseEntity<BookingResponse> book(@RequestBody BookingRequest request,
                                                Authentication authentication) {
        BookingResponse booking = bookingService.book(authentication.getName(),
                request.trainId(), request.seatClass(), request.journeyDate(), request.passengers(),
                request.allowWaitlist(), request.fromStation(), request.toStation());
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/my")
    public List<BookingResponse> myBookings(Authentication authentication) {
        return bookingService.myBookings(authentication.getName());
    }

    // admin only (enforced in SecurityConfig) - all bookings in the system
    @GetMapping("/all")
    public List<BookingResponse> allBookings() {
        return bookingService.allBookings();
    }

    // confirm payment after the Razorpay checkout succeeds
    @PutMapping("/{id}/pay")
    public BookingResponse pay(@PathVariable Long id, @RequestBody PaymentRequest payment,
                               Authentication authentication) {
        return bookingService.confirmPayment(id, authentication.getName(), payment);
    }

    // cancel the whole booking
    @PutMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, Authentication authentication) {
        return bookingService.cancel(id, authentication.getName());
    }

    // partial cancellation: cancel a single passenger
    @PutMapping("/{bookingId}/passengers/{passengerId}/cancel")
    public BookingResponse cancelPassenger(@PathVariable Long bookingId,
                                           @PathVariable Long passengerId,
                                           Authentication authentication) {
        return bookingService.cancelPassenger(bookingId, passengerId, authentication.getName());
    }
}
