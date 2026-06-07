package com.example.payments.mock.regulatory.api;

import static com.example.payments.mock.regulatory.common.RegulatoryConstants.REGULATORY_PATH;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.REPORT_PATH;

import com.example.payments.mock.regulatory.application.RegulatoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(REGULATORY_PATH)
@RequiredArgsConstructor
public class RegulatoryController {

  private final RegulatoryService regulatoryService;

  @PostMapping(REPORT_PATH)
  public ResponseEntity<String> receiveReport(@RequestBody RegulatoryReportRequest request) {
    try {
      String response = regulatoryService.processReport(request);
      return ResponseEntity.ok(response);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }
}
