package com.example.payments.export.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.payments.export.mapper.BatchMapper;
import com.example.payments.export.mapper.BatchMapperImpl;
import com.example.payments.export.model.ExecutionDetail;
import com.example.payments.export.model.JobDetail;
import com.example.payments.export.model.JobSummary;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.http.ResponseEntity;

class BatchDashboardControllerTest {

  private static final String JOB = "job1";
  private JobExplorer jobExplorer;
  private BatchDashboardController controller;

  @BeforeEach
  void setUp() {
    jobExplorer = mock(JobExplorer.class);
    BatchMapper batchMapper = new BatchMapperImpl();
    controller = new BatchDashboardController(jobExplorer, batchMapper);
  }

  @Test
  void testGetJobSummaries() throws NoSuchJobException {
    when(jobExplorer.getJobNames()).thenReturn(List.of(JOB));
    when(jobExplorer.getJobInstanceCount(JOB)).thenReturn(1L);

    JobInstance instance = new JobInstance(1L, JOB);
    when(jobExplorer.getJobInstances(JOB, 0, 1)).thenReturn(List.of(instance));

    JobExecution execution = new JobExecution(1L);
    execution.setStatus(BatchStatus.COMPLETED);
    when(jobExplorer.getJobExecutions(instance)).thenReturn(List.of(execution));

    ResponseEntity<List<JobSummary>> response = controller.getJobSummaries();
    assertEquals(JOB, response.getBody().get(0).getJobName());
  }

  @Test
  void testGetJobSummariesWithException() throws NoSuchJobException {
    when(jobExplorer.getJobNames()).thenReturn(List.of(JOB));
    when(jobExplorer.getJobInstanceCount(JOB)).thenThrow(new NoSuchJobException("not found"));
    when(jobExplorer.getJobInstances(JOB, 0, 1)).thenReturn(Collections.emptyList());

    ResponseEntity<List<JobSummary>> response = controller.getJobSummaries();
    assertEquals(1, response.getBody().size());
  }

  @Test
  void testGetJobDetail() throws NoSuchJobException {
    when(jobExplorer.getJobInstanceCount(JOB)).thenReturn(5L);
    JobInstance instance = new JobInstance(1L, JOB);
    when(jobExplorer.getJobInstances(JOB, 0, 10)).thenReturn(List.of(instance));
    when(jobExplorer.getJobExecutions(instance)).thenReturn(Collections.emptyList());

    ResponseEntity<JobDetail> response = controller.getJobDetail(JOB, null);
    assertEquals(JOB, response.getBody().getJobName());
  }

  @Test
  void testGetExecutionDetail() {
    JobExecution ex = createExecution();
    when(jobExplorer.getJobExecution(1L)).thenReturn(ex);
    ResponseEntity<ExecutionDetail> response = controller.getExecutionDetail(1L);
    assertEquals(1L, response.getBody().getExecution().getId());
  }

  private JobExecution createExecution() {
    JobParameters params = new JobParametersBuilder().addString("key", "val").toJobParameters();
    JobExecution execution = new JobExecution(instance(1L, JOB), 1L, params);
    StepExecution step1 = new StepExecution("step1", execution);
    StepExecution step2 = new StepExecution("step2:partition0", execution);
    execution.addStepExecutions(List.of(step1, step2));
    return execution;
  }

  @Test
  void testGetExecutionDetailNotFound() {
    when(jobExplorer.getJobExecution(1L)).thenReturn(null);
    ResponseEntity<ExecutionDetail> response = controller.getExecutionDetail(1L);
    assertEquals(404, response.getStatusCode().value());
  }

  private JobInstance instance(long id, String name) {
    return new JobInstance(id, name);
  }
}
