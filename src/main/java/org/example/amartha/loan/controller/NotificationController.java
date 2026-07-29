package org.example.amartha.loan.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.amartha.loan.dto.NotificationResponse;
import org.example.amartha.loan.repository.NotificationOutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Notification status query API.
 * <p>{@code GET /api/loans/notifications/{id}} — returns per-investor send status.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/loans")
public class NotificationController {

    private final NotificationOutboxRepository outboxRepository;

    public NotificationController(NotificationOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/notifications/{id}")
    public ResponseEntity<List<NotificationResponse>> getNotifications(@PathVariable Long id) {
        log.info("GET /api/loans/notifications/{}", id);
        var responses = outboxRepository.findByLoanId(id).stream()
            .map(NotificationResponse::from)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
