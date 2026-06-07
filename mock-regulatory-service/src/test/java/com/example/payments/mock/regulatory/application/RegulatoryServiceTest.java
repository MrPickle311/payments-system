package com.example.payments.mock.regulatory.application;

import static com.example.payments.mock.regulatory.common.RegulatoryConstants.ACCEPTED_RESPONSE;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.CHAOS_RESPONSE;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.DUPLICATE_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.example.payments.mock.regulatory.api.RegulatoryReportRequest;
import com.example.payments.mock.regulatory.config.RegulatoryProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegulatoryServiceTest {

  @Mock
  private RegulatoryProperties properties;

  @InjectMocks
  private RegulatoryService service;

  private RegulatoryReportRequest request;

  @BeforeEach
  void setUp() {
    request = RegulatoryReportRequest.builder().reportId("report-123")
        .payments(List.of(RegulatoryReportRequest.ExportedPayment.builder().build())).build();
  }

  @Test
  void testProcessReportSuccess() {
    when(properties.getFailureRate()).thenReturn(0.0);
    String response = service.processReport(request);
    assertEquals(ACCEPTED_RESPONSE, response);
  }

  @Test
  void testProcessReportDuplicate() {
    when(properties.getFailureRate()).thenReturn(0.0);
    service.processReport(request);

    String response = service.processReport(request);
    assertEquals(DUPLICATE_RESPONSE, response);
  }

  @Test
  void testProcessReportChaosMode() {
    when(properties.getFailureRate()).thenReturn(1.0);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.processReport(request));
    assertEquals(CHAOS_RESPONSE, ex.getMessage());
  }
}
