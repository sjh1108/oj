package dev.algoj.global.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bot-only status endpoint; /api/internal/** is guarded by BotApiKeyFilter. */
@RestController
@RequestMapping("/api/internal/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping
    public ResponseEntity<MonitorResponse> snapshot() {
        return ResponseEntity.ok(monitorService.snapshot());
    }
}
