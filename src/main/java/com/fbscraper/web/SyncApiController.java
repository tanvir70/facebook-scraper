package com.fbscraper.web;

import com.fbscraper.model.facebook.FacebookSyncResult;
import com.fbscraper.model.instagram.InstagramSyncResult;
import com.fbscraper.service.SentimentSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AtomicReference<FacebookSyncResult> latestFbResult;
    private final AtomicReference<InstagramSyncResult> latestIgResult;

    public SyncApiController(SentimentSyncService syncService) {
        this.syncService = syncService;
        this.latestFbResult = new AtomicReference<>(syncService.loadPreviousFacebookResult().orElse(null));
        this.latestIgResult = new AtomicReference<>(syncService.loadPreviousInstagramResult().orElse(null));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestParam(value = "platform", defaultValue = "facebook") String platform) {
        if (!syncing.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A synchronization is already running"));
        }

        try {
            boolean isIg = "instagram".equalsIgnoreCase(platform);
            if (isIg) {
                InstagramSyncResult result = syncService.syncInstagram();
                latestIgResult.set(result);
                return ResponseEntity.ok(result);
            } else {
                FacebookSyncResult result = syncService.syncFacebook();
                latestFbResult.set(result);
                return ResponseEntity.ok(result);
            }
        } catch (RuntimeException e) {
            String message = e.getMessage();
            String error = (message == null || message.isBlank()) ? "Synchronization failed" : message;
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", error));
        } finally {
            syncing.set(false);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(value = "platform", defaultValue = "facebook") String platform) {
        boolean isIg = "instagram".equalsIgnoreCase(platform);
        Object result = isIg ? latestIgResult.get() : latestFbResult.get();
        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "NOT_SYNCED",
                    "syncing", syncing.get(),
                    "platform", isIg ? "instagram" : "facebook"
            ));
        }
        return ResponseEntity.ok(result);
    }

    public Optional<FacebookSyncResult> latestFacebookResult() {
        return Optional.ofNullable(latestFbResult.get());
    }

    public Optional<InstagramSyncResult> latestInstagramResult() {
        return Optional.ofNullable(latestIgResult.get());
    }

    public Optional<FacebookSyncResult> latestResult() {
        return latestFacebookResult();
    }
}
