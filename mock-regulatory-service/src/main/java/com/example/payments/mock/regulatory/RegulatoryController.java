package com.example.payments.mock.regulatory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatoryController {

    private final Random random = new Random();
    private final Set<String> seenReportIds = new HashSet<>();

    @Value("${regulatory.failure-rate:0.2}")
    private double failureRate;

    @PostMapping("/report")
    public ResponseEntity<String> receiveReport(@RequestBody RegulatoryReportRequest request) {
        if (seenReportIds.contains(request.getReportId())) {
            log.warn("[MockRegulatory] IDEMPOTENCY HIT: Already processed reportId={}. Returning 200 OK.", request.getReportId());
            return ResponseEntity.ok("Duplicate - Already Processed");
        }

        log.info("[MockRegulatory] Received report: id={}, paymentCount={}", 
                request.getReportId(), request.getPayments().size());

        if (random.nextDouble() < failureRate) {
            log.error("[MockRegulatory] CHAOS MODE: Simulating 500 Internal Server Error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Chaos Mode: Random Failure");
        }

        seenReportIds.add(request.getReportId());
        log.info("[MockRegulatory] Report processed successfully");
        return ResponseEntity.ok("Accepted");
    }
}
