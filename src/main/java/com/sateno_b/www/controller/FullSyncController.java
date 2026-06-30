package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.SyncStatusDto;
import com.sateno_b.www.service.FullSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/full_sync")
@RequiredArgsConstructor
public class FullSyncController {

    private final FullSyncService fullSyncService;

    @PostMapping("/start/{siteId}")
    public ResponseEntity<?> start(@PathVariable Long siteId,
                                   @RequestParam(defaultValue = "false") boolean importOrders) {
        try {
            fullSyncService.startSync(siteId, importOrders);
            return ResponseEntity.ok(fullSyncService.getStatus());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/status")
    public ResponseEntity<SyncStatusDto> status() {
        return ResponseEntity.ok(fullSyncService.getStatus());
    }
}
