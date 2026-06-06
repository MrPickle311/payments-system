package com.example.payments.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.ExpectedCount.min;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.config.ExportProperties;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(properties = {"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "export.batch-size=2", "export.grid-size=3",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2." + "Driver", "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect." + "H2Dialect",
    "spring.batch.jdbc.initialize-schema=always", "export.schedule=-"})
@SpringBatchTest
@EmbeddedKafka(partitions = 3, topics = {"payment-ledger-events"})
@DirtiesContext
@ActiveProfiles("test")
class ExportJobIntegrationTest {

  @Autowired
  private JobLauncherTestUtils jobLauncherTestUtils;

  @Autowired
  private JobRepositoryTestUtils jobRepositoryTestUtils;

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Job exportLedgerJob;

  @Autowired
  private JobLauncher jobLauncher;

  @Autowired
  private JobRepository jobRepository;

  @Autowired
  private ExportProperties exportProperties;

  private MockRestServiceServer mockServer;

  @TestConfiguration
  static class KafkaTestConfig {
    @Bean
    public ConsumerFactory<Object, Object> stringConsumerFactory(
        @Value("${spring.embedded.kafka.brokers}") String brokers) {
      Map<String, Object> props = new HashMap<>();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, String> producerFactory(
        @Value("${spring.embedded.kafka.brokers}") String brokers) {
      Map<String, Object> props = new HashMap<>();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
        ProducerFactory<String, String> producerFactory) {
      return new KafkaTemplate<>(producerFactory);
    }
  }

  @BeforeEach
  void setUp() {
    jobLauncherTestUtils.setJob(exportLedgerJob);
    jobLauncherTestUtils.setJobLauncher(jobLauncher);
    jobLauncherTestUtils.setJobRepository(jobRepository);
    jobRepositoryTestUtils.removeJobExecutions();
    mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void shouldExportLedgerEventsSuccessfully() throws Exception {
    sendTestEventToKafka(createEvent(1L, "100.00", "USD"), 0, "1");
    sendTestEventToKafka(createEvent(2L, "200.00", "EUR"), 1, "2");

    Thread.sleep(2000);

    setupMockServerExpectations();

    JobExecution jobExecution = jobLauncherTestUtils.launchJob(
        new JobParametersBuilder().addLong("time", System.currentTimeMillis()).toJobParameters());

    assertThat(jobExecution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");
    mockServer.verify();
  }

  private LedgerEvent createEvent(long id, String gross, String curr) {
    return LedgerEvent.builder().paymentId(id).grossAmount(new BigDecimal(gross))
        .netAmount(new BigDecimal(gross)).currency(curr).timestamp(LocalDateTime.now()).build();
  }

  private void sendTestEventToKafka(LedgerEvent event, int partition, String key)
      throws JsonProcessingException {
    kafkaTemplate.send(exportProperties.getTopic(), partition, key,
        objectMapper.writeValueAsString(event));
  }

  private void setupMockServerExpectations() {
    mockServer.expect(min(1), requestTo(exportProperties.getRegulatory().getUrl()))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));
  }
}
