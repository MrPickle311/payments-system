package com.example.payments.mock.regulatory.api;

import static com.example.payments.mock.regulatory.common.RegulatoryConstants.REGULATORY_PATH;
import static com.example.payments.mock.regulatory.common.RegulatoryConstants.REPORT_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payments.mock.regulatory.application.RegulatoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegulatoryController.class)
class RegulatoryControllerTest {

  private static final String REPORT_ID = "report-123";
  private static final String ACCEPTED = "ACCEPTED";
  private static final String CHAOS = "CHAOS";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RegulatoryService regulatoryService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void testReceiveReportSuccess() throws Exception {
    RegulatoryReportRequest request =
        RegulatoryReportRequest.builder().reportId(REPORT_ID).payments(List.of()).build();

    when(regulatoryService.processReport(any(RegulatoryReportRequest.class))).thenReturn(ACCEPTED);

    mockMvc
        .perform(post(REGULATORY_PATH + REPORT_PATH).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk()).andExpect(content().string(ACCEPTED));
  }

  @Test
  void testReceiveReportFailure() throws Exception {
    RegulatoryReportRequest request =
        RegulatoryReportRequest.builder().reportId(REPORT_ID).payments(List.of()).build();

    when(regulatoryService.processReport(any(RegulatoryReportRequest.class)))
        .thenThrow(new IllegalStateException(CHAOS));

    mockMvc
        .perform(post(REGULATORY_PATH + REPORT_PATH).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError()).andExpect(content().string(CHAOS));
  }
}
