package com.tatakai.manager.controller;

import com.tatakai.manager.dto.request.CreateBookingRequest;
import com.tatakai.manager.dto.response.BookingResponse;
import com.tatakai.manager.security.AuthenticatedUser;
import com.tatakai.manager.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/campaigns/{campaignId}/timeskips/{timeSkipId}/bookings")
    public ResponseEntity<BookingResponse> book(@PathVariable UUID campaignId,
                                                @PathVariable UUID timeSkipId,
                                                @Valid @RequestBody CreateBookingRequest request) {
        BookingResponse res = bookingService.book(campaignId, timeSkipId, AuthenticatedUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/campaigns/{campaignId}/timeskips/{timeSkipId}/bookings")
    public ResponseEntity<List<BookingResponse>> list(@PathVariable UUID campaignId,
                                                      @PathVariable UUID timeSkipId) {
        return ResponseEntity.ok(
                bookingService.listBookings(campaignId, timeSkipId, AuthenticatedUser.id()));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID bookingId) {
        bookingService.cancel(bookingId, AuthenticatedUser.id());
        return ResponseEntity.noContent().build();
    }
}
