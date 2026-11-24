package com.civicsense.health;

import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.observability.CorrelationId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/meta")
public class MetaHealthController {

    private final InfrastructureHealthService service;
    private final String version;

    public MetaHealthController(
            InfrastructureHealthService service,
            @Value("${civicsense.app-version}") String version) {
        this.service = service;
        this.version = version;
    }

    public record HealthResponse(
            String status,
            String version,
            String time,
            String database,
            String postgis,
            String objectStorage) {
    }

    @GetMapping("/health")
    ResponseEntity<HealthResponse> health(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(CorrelationId.HEADER, CorrelationId.getOrCreate(request));

        String database = check(service::plainSqlWorks);
        String postgis = check(service::postgisWorks);
        String objectStorage = check(service::objectStorageWorks);
        boolean allUp = "UP".equals(database)
                && "UP".equals(postgis)
                && "UP".equals(objectStorage);

        HealthResponse body = new HealthResponse(
                allUp ? "UP" : "DOWN",
                version,
                Instant.now().toString(),
                database,
                postgis,
                objectStorage);

        return ResponseEntity
                .status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private String check(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean() ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
