package com.example.payments.export.web;

import com.example.payments.export.api.BatchApi;
import com.example.payments.export.mapper.BatchMapper;
import com.example.payments.export.model.ExecutionDetail;
import com.example.payments.export.model.JobDetail;
import com.example.payments.export.model.JobSummary;
import com.example.payments.export.model.StepDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BatchDashboardController implements BatchApi {

  private final JobExplorer jobExplorer;
  private final BatchMapper batchMapper;

  @Override
  public ResponseEntity<List<JobSummary>> getJobSummaries() {
    List<JobSummary> summaries = jobExplorer.getJobNames().stream().map(this::buildJobSummary)
        .sorted(Comparator.comparing(JobSummary::getJobName)).collect(Collectors.toList());
    return ResponseEntity.ok(summaries);
  }

  @Override
  public ResponseEntity<JobDetail> getJobDetail(String jobName, Integer page) {
    int pageSize = 10;
    int currentPage = page != null ? page : 0;
    List<JobInstance> instances = new ArrayList<>();
    long totalInstances = 0;
    try {
      instances = jobExplorer.getJobInstances(jobName, currentPage * pageSize, pageSize);
      totalInstances = jobExplorer.getJobInstanceCount(jobName);
    } catch (NoSuchJobException e) {
      // Job doesn't exist yet, show empty list
    }

    List<com.example.payments.export.model.JobInstanceDetail> details = instances.stream()
        .map(instance -> buildInstanceDetail(instance, jobExplorer.getJobExecutions(instance)))
        .sorted(Comparator
            .comparingLong(com.example.payments.export.model.JobInstanceDetail::getInstanceId)
            .reversed())
        .collect(Collectors.toList());

    return ResponseEntity.ok(batchMapper.toJobDetail(jobName, details, currentPage, pageSize,
        (int) Math.ceil((double) totalInstances / pageSize), totalInstances));
  }

  @Override
  public ResponseEntity<ExecutionDetail> getExecutionDetail(Long executionId) {
    JobExecution execution = jobExplorer.getJobExecution(executionId);
    if (execution == null) {
      return ResponseEntity.notFound().build();
    }

    List<StepDetail> steps = execution.getStepExecutions().stream().map(batchMapper::toStepDetail)
        .collect(Collectors.toList());

    List<StepDetail> managerSteps =
        steps.stream().filter(s -> s.getStepName() != null && !s.getStepName().contains(":"))
            .collect(Collectors.toList());

    List<StepDetail> partitionSteps =
        steps.stream().filter(s -> s.getStepName() != null && s.getStepName().contains(":"))
            .sorted(Comparator.comparing(StepDetail::getStepName)).collect(Collectors.toList());

    java.util.Map<String, String> params = new java.util.HashMap<>();
    execution.getJobParameters().getParameters()
        .forEach((k, v) -> params.put(k, String.valueOf(v.getValue())));

    long totalRead = partitionSteps.stream().mapToLong(StepDetail::getReadCount).sum();
    long totalWrite = partitionSteps.stream().mapToLong(StepDetail::getWriteCount).sum();
    long totalSkip = partitionSteps.stream().mapToLong(StepDetail::getSkipCount).sum();

    return ResponseEntity.ok(batchMapper.toExecutionDetail(batchMapper.toJobExecution(execution),
        batchMapper.toJobInstance(execution.getJobInstance()), params, managerSteps, partitionSteps,
        !partitionSteps.isEmpty(), totalRead, totalWrite, totalSkip));
  }

  private JobSummary buildJobSummary(String jobName) {
    long count = 0;
    List<JobInstance> recent = new ArrayList<>();
    try {
      count = jobExplorer.getJobInstanceCount(jobName);
      recent = jobExplorer.getJobInstances(jobName, 0, 1);
    } catch (NoSuchJobException e) {
      // Ignore, counts stay at 0
    }

    String lastStatus = "NEVER_RUN";
    int totalExecutions = 0;
    if (!recent.isEmpty()) {
      List<JobExecution> execs = jobExplorer.getJobExecutions(recent.get(0));
      if (!execs.isEmpty()) {
        lastStatus = execs.get(0).getStatus().name();
        totalExecutions = execs.size();
      }
    }
    return batchMapper.toJobSummary(jobName, count, lastStatus, totalExecutions);
  }

  private com.example.payments.export.model.JobInstanceDetail buildInstanceDetail(
      JobInstance instance, List<JobExecution> executions) {
    String latestStatus = executions.isEmpty() ? "UNKNOWN" : executions.get(0).getStatus().name();
    List<com.example.payments.export.model.JobExecution> apiExecutions =
        executions.stream().map(batchMapper::toJobExecution).collect(Collectors.toList());
    return batchMapper.toJobInstanceDetail(instance, latestStatus, apiExecutions);
  }
}
