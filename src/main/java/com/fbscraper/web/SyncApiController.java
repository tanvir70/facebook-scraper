package com.fbscraper.web;

import com.fbscraper.model.SyncResult;
import com.fbscraper.service.SentimentSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api")
public class SyncApiController {

    private final SentimentSyncService syncService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicReference<SyncResult> latestResult;

    public SyncApiController(SentimentSyncService syncService) {
        this.syncService = syncService;
        this.latestResult = new AtomicReference<>(syncService.loadPreviousResult().orElse(null));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        if (!syncing.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A synchronization is already running"));
        }

        try {
            SyncResult result = syncService.sync();
            latestResult.set(result);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            String error = (message == null || message.isBlank()) ? "Synchronization failed" : message;
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", error));
        } finally {
            syncing.set(false);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        SyncResult result = latestResult.get();
        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "NOT_SYNCED", "syncing", syncing.get()));
        }
        return ResponseEntity.ok(result);
    }

    public Optional<SyncResult> latestResult() {
        return Optional.ofNullable(latestResult.get());
    }
}
