package com.restopilot.backend.core.controller;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReadinessController {
    private final DataSource dataSource;

    /** Render readiness: validates the database without exposing connection details. */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (SQLException ignored) {
            // No database or credential details in the public response.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
