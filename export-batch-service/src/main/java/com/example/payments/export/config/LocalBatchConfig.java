package com.example.payments.export.config;

import com.example.payments.common.dto.LedgerEvent;
import com.example.payments.export.job.OffsetRangePartitioner;
import com.example.payments.export.job.PaymentIdTrackingListener;
import com.example.payments.export.writer.RegulatoryApiWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LocalBatchConfig {

  public static final String EXPORT_LEDGER_JOB_NAME = "exportLedgerJob";
  public static final String MANAGER_STEP_NAME = "managerStep";

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;
  private final KafkaItemReader<String, String> kafkaItemReader;
  private final RegulatoryApiWriter regulatoryApiWriter;
  private final ObjectMapper objectMapper;
  private final ExportProperties exportProperties;


  @Bean
  public Job exportLedgerJob(Step reconciliationStep, Step externalReconciliationStep, Step managerStep) {
    return new JobBuilder(EXPORT_LEDGER_JOB_NAME, jobRepository).incrementer(new RunIdIncrementer())
        .start(reconciliationStep).next(externalReconciliationStep).next(managerStep).build();
  }

  @Bean
  public Step reconciliationStep(JdbcTemplate jdbcTemplate) {
    return new StepBuilder("reconciliationStep", jobRepository)
        .tasklet(reconciliationTasklet(jdbcTemplate), transactionManager).build();
  }

  @Bean
  public Step externalReconciliationStep(JdbcTemplate jdbcTemplate) {
    return new StepBuilder("externalReconciliationStep", jobRepository)
        .tasklet(externalReconciliationTasklet(jdbcTemplate), transactionManager).build();
  }

  private Tasklet reconciliationTasklet(JdbcTemplate jdbcTemplate) {
    return (contribution, chunkContext) -> {
      performReconciliation(jdbcTemplate);
      return RepeatStatus.FINISHED;
    };
  }

  private Tasklet externalReconciliationTasklet(JdbcTemplate jdbcTemplate) {
    return (contribution, chunkContext) -> {
      performExternalReconciliation(jdbcTemplate);
      return RepeatStatus.FINISHED;
    };
  }

  private void performReconciliation(JdbcTemplate jdbcTemplate) {
    log.info("[Reconciliation] Starting EOD balance verification...");
    List<String> currencies = jdbcTemplate.queryForList(
        "SELECT DISTINCT currency FROM wallet_accounts UNION SELECT DISTINCT currency FROM ledger_entries",
        String.class);
    currencies.forEach(currency -> validateCurrencyReconciliation(jdbcTemplate, currency));
    log.info("[Reconciliation] EOD Reconciliation successful. All currencies match.");
  }

  private void validateCurrencyReconciliation(JdbcTemplate jdbcTemplate, String currency) {
    BigDecimal walletSum = jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(balance), 0) FROM wallet_accounts WHERE currency = ?",
        BigDecimal.class, currency);
    BigDecimal ledgerSum = jdbcTemplate.queryForObject(
        "SELECT COALESCE(SUM(gross_amount), 0) FROM ledger_entries WHERE currency = ?",
        BigDecimal.class, currency);
    BigDecimal actualTotal = walletSum.add(ledgerSum);
    BigDecimal expected =
        exportProperties.getExpectedTotals().getOrDefault(currency, BigDecimal.ZERO);
    checkDiscrepancy(currency, actualTotal, expected);
  }

  private void checkDiscrepancy(String currency, BigDecimal actualTotal, BigDecimal expected) {
    log.info("[Reconciliation] Currency: {}, Total: {}, Expected: {}", currency, actualTotal,
        expected);
    if (actualTotal.compareTo(expected) != 0) {
      log.error(
          "🔴 CRITICAL ALERT: EOD Reconciliation failed for currency {}! Total: {}, Expected: {}",
          currency, actualTotal, expected);
      throw new IllegalStateException(
          "Reconciliation discrepancy detected for " + currency + "! Blocking export.");
    }
  }

  @Bean
  public Step managerStep(TaskExecutorPartitionHandler partitionHandler, Partitioner partitioner) {
    return new StepBuilder(MANAGER_STEP_NAME, jobRepository)
        .partitioner(ExportConstants.WORKER_STEP_NAME, partitioner)
        .partitionHandler(partitionHandler).gridSize(exportProperties.getGridSize()).build();
  }

  @Bean
  public Partitioner partitioner() {
    return new OffsetRangePartitioner();
  }

  @Bean
  public TaskExecutorPartitionHandler partitionHandler(Step workerStep, TaskExecutor taskExecutor) {
    TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
    handler.setGridSize(exportProperties.getGridSize());
    handler.setTaskExecutor(taskExecutor);
    handler.setStep(workerStep);
    return handler;
  }

  @Bean
  public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(exportProperties.getGridSize());
    executor.setMaxPoolSize(exportProperties.getGridSize());
    executor.setThreadNamePrefix("partition_thread");
    executor.initialize();
    return executor;
  }

  @Bean
  public Step workerStep(PaymentIdTrackingListener trackingListener) {
    return new StepBuilder(ExportConstants.WORKER_STEP_NAME, jobRepository)
        .<String, LedgerEvent>chunk(exportProperties.getBatchSize(), transactionManager)
        .reader(kafkaItemReader).processor(eventProcessor()).writer(regulatoryApiWriter)
        .listener(trackingListener).faultTolerant().retryLimit(3)
        .retry(ResourceAccessException.class).retry(HttpServerErrorException.class).build();
  }

  @Bean
  public ItemProcessor<String, LedgerEvent> eventProcessor() {
    return item -> {
      try {
        LedgerEvent event = objectMapper.readValue(item, LedgerEvent.class);
        log.debug("[WorkerProcessor] Parsed event for paymentId: {}", event.getPaymentId());
        return event;
      } catch (Exception exception) {
        log.error("[WorkerProcessor] Failed to parse JSON: {}", item);
        return null;
      }
    };
  }

  private void performExternalReconciliation(JdbcTemplate jdbcTemplate) {
    log.info("[ExternalRecon] Starting CAMT.053 reconciliation...");
    java.io.File folder = new java.io.File("bank-statements");
    if (!folder.exists() || folder.listFiles() == null) {
      log.warn("[ExternalRecon] No bank statements directory found or empty. Skipping.");
      return;
    }
    for (java.io.File file : folder.listFiles()) {
      if (file.getName().endsWith(".xml")) {
        processCamtFile(jdbcTemplate, file);
      }
    }
  }

  private void processCamtFile(JdbcTemplate jdbcTemplate, java.io.File file) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      Document doc = factory.newDocumentBuilder().parse(file);
      NodeList entries = doc.getElementsByTagName("Ntry");
      for (int i = 0; i < entries.getLength(); i++) {
        reconcileEntry(jdbcTemplate, (Element) entries.item(i));
      }
    } catch (Exception e) {
      log.error("[ExternalRecon] Error parsing CAMT file: {}", file.getName(), e);
    }
  }

  private void reconcileEntry(JdbcTemplate jdbcTemplate, Element ntry) {
    try {
      Element amtEl = (Element) ntry.getElementsByTagName("Amt").item(0);
      BigDecimal amount = new BigDecimal(amtEl.getTextContent());
      String currency = amtEl.getAttribute("Ccy");
      String txId = getElementTextOrNull(ntry, "EndToEndId");
      String finalTxId = txId != null ? txId : getElementTextOrNull(ntry, "TxId");
      if (finalTxId != null) {
        verifyBankTransaction(jdbcTemplate, finalTxId, amount, currency);
      }
    } catch (Exception e) {
      log.warn("[ExternalRecon] Error reconciling entry", e);
    }
  }

  private String getElementTextOrNull(Element parent, String tag) {
    NodeList nl = parent.getElementsByTagName(tag);
    return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
  }

  private void verifyBankTransaction(JdbcTemplate jdbcTemplate, String txId, BigDecimal amount, String ccy) {
    List<BigDecimal> dbAmounts = jdbcTemplate.queryForList(
        "SELECT amount FROM payments WHERE transaction_id = ? AND state = 'COMPLETED'",
        BigDecimal.class, txId);
    if (dbAmounts.isEmpty()) {
      log.error("🔴 CRITICAL: Bank tx {} not found/COMPLETED in DB!", txId);
      return;
    }
    if (dbAmounts.get(0).compareTo(amount) != 0) {
      log.error("🔴 CRITICAL: Bank tx {} amount mismatch! Bank: {}, DB: {}", txId, amount, dbAmounts.get(0));
      return;
    }
    log.info("✅ Bank tx {} reconciled successfully.", txId);
  }
}
