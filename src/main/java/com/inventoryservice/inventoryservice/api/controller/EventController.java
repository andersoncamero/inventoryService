package com.inventoryservice.inventoryservice.api.controller;

import com.inventoryservice.inventoryservice.domain.dtos.CreateEventRequest;
import com.inventoryservice.inventoryservice.domain.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody CreateEventRequest request) {
        service.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
