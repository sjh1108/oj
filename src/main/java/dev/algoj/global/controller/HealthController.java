package dev.algoj.global.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    // Readiness probe: 200 only when the DB is reachable. The blue-green deploy
    // gates traffic on this, so a container that can't reach MySQL is never
    // switched in.
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = isDbUp();
        Map<String, Object> body = Map.of(
                "status", dbUp ? "UP" : "DOWN",
                "db", dbUp ? "UP" : "DOWN",
                "timestamp", LocalDateTime.now()
        );
        return dbUp
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean isDbUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }
}
