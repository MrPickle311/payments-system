package com.example.payments.export.web;

import com.example.payments.export.api.BatchApi;
import com.example.payments.export.mapper.BatchMapper;
import com.example.payments.export.model.ExecutionDetail;
import com.example.payments.export.model.JobDetail;
import com.example.payments.export.model.JobInstanceDetail;
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
import java.util.Map;
import java.util.HashMap;
import java.util.function.ToLongFunction;

@RestController
@RequiredArgsConstructor
public class BatchDashboardController implements BatchApi {

  private final JobExplorer jobExplorer;
  private final BatchMapper batchMapper;

  @Override
  public ResponseEntity<List<JobSummary>> getJobSummaries() {
    List<JobSummary> summaries = jobExplorer.getJobNames().stream().map(this::buildJobSummary)
        .sorted(Comparator.comparing(JobSummary::getJobName)).toList();
    return ResponseEntity.ok(summaries);
  }

  @Override
  public ResponseEntity<JobDetail> getJobDetail(String jobName, Integer page) {
    int p = page != null ? page : 0;
    long tot = safeGetJobInstanceCount(jobName);
    List<JobInstanceDetail> dets = safeGetJobInstances(jobName, p, 10).stream()
        .map(i -> buildInstanceDetail(i, jobExplorer.getJobExecutions(i)))
        .sorted(Comparator.comparingLong(JobInstanceDetail::getInstanceId).reversed()).toList();
    int totPages = (int) Math.ceil((double) tot / 10);
    return ResponseEntity.ok(batchMapper
        .toJobDetail(new BatchMapper.JobDetailContext(jobName, dets, p, 10, totPages, tot)));
  }

  private long safeGetJobInstanceCount(String jobName) {
    try {
      return jobExplorer.getJobInstanceCount(jobName);
    } catch (NoSuchJobException e) {
      return 0;
    }
  }

  private List<JobInstance> safeGetJobInstances(String jobName, int page, int size) {
    return jobExplorer.getJobInstances(jobName, page * size, size);
  }

  @Override
  public ResponseEntity<ExecutionDetail> getExecutionDetail(Long executionId) {
    JobExecution ex = jobExplorer.getJobExecution(executionId);
    if (ex == null) {
      return ResponseEntity.notFound().build();
    }
    List<StepDetail> st = ex.getStepExecutions().stream().map(batchMapper::toStepDetail).toList();
    return ResponseEntity.ok(batchMapper.toExecutionDetail(buildCtx(ex, st)));
  }

  private BatchMapper.ExecutionDetailContext buildCtx(JobExecution ex, List<StepDetail> st) {
    List<StepDetail> mSt = filterSteps(st, false);
    List<StepDetail> pSt = filterSteps(st, true);
    return new BatchMapper.ExecutionDetailContext(batchMapper.toJobExecution(ex),
        batchMapper.toJobInstance(ex.getJobInstance()), getParams(ex), mSt, pSt, !pSt.isEmpty(),
        sum(pSt, StepDetail::getReadCount), sum(pSt, StepDetail::getWriteCount),
        sum(pSt, StepDetail::getSkipCount));
  }

  private List<StepDetail> filterSteps(List<StepDetail> st, boolean isPartition) {
    return st.stream()
        .filter(s -> s.getStepName() != null && s.getStepName().contains(":") == isPartition)
        .toList();
  }

  private Map<String, String> getParams(JobExecution ex) {
    Map<String, String> p = new HashMap<>();
    ex.getJobParameters().getParameters().forEach((k, v) -> p.put(k, String.valueOf(v.getValue())));
    return p;
  }

  private long sum(List<StepDetail> steps, ToLongFunction<StepDetail> mapper) {
    return steps.stream().mapToLong(mapper).sum();
  }

  private JobSummary buildJobSummary(String jobName) {
    long count = safeGetJobInstanceCount(jobName);
    List<JobInstance> recent = jobExplorer.getJobInstances(jobName, 0, 1);
    List<JobExecution> ex =
        recent.isEmpty() ? new ArrayList<>() : jobExplorer.getJobExecutions(recent.get(0));
    return batchMapper.toJobSummary(jobName, count,
        ex.isEmpty() ? "NEVER_RUN" : ex.get(0).getStatus().name(), ex.size());
  }

  private JobInstanceDetail buildInstanceDetail(JobInstance instance,
      List<JobExecution> executions) {
    String latestStatus = executions.isEmpty() ? "UNKNOWN" : executions.get(0).getStatus().name();
    return batchMapper.toJobInstanceDetail(instance, latestStatus, executions);
  }
}
