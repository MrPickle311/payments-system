package com.example.payments.export.mapper;

import com.example.payments.export.model.ExecutionDetail;
import com.example.payments.export.model.JobDetail;
import com.example.payments.export.model.JobInstanceDetail;
import com.example.payments.export.model.JobSummary;
import com.example.payments.export.model.StepDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.step.StepExecution;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Mapper(componentModel = "spring")
public interface BatchMapper {

  JobSummary toJobSummary(String jobName, long instanceCount, String lastStatus,
      int totalExecutions);

  record JobDetailContext(String jobName, List<JobInstanceDetail> instances, int page, int pageSize,
      int totalPages, long totalInstances) {}

  @Mapping(target = "jobName", source = "jobName")
  @Mapping(target = "instances", source = "instances")
  @Mapping(target = "page", source = "page")
  @Mapping(target = "pageSize", source = "pageSize")
  @Mapping(target = "totalPages", source = "totalPages")
  @Mapping(target = "totalInstances", source = "totalInstances")
  JobDetail toJobDetail(JobDetailContext context);

  @Mapping(target = "instanceId", source = "jobInstance.instanceId")
  @Mapping(target = "jobName", source = "jobInstance.jobName")
  JobInstanceDetail toJobInstanceDetail(JobInstance jobInstance, String latestStatus,
      List<JobExecution> executions);

  @Mapping(target = "id", source = "id")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "status", expression = "java(jobExecution.getStatus().name())")
  @Mapping(target = "startTime", source = "startTime", qualifiedByName = "toOffsetDateTime")
  @Mapping(target = "endTime", source = "endTime", qualifiedByName = "toOffsetDateTime")
  @Mapping(target = "exitStatus.exitCode", source = "exitStatus.exitCode")
  @Mapping(target = "exitStatus.exitDescription", source = "exitStatus.exitDescription")
  com.example.payments.export.model.JobExecution toJobExecution(JobExecution jobExecution);

  record ExecutionDetailContext(
      com.example.payments.export.model.JobExecution apiExecution,
      com.example.payments.export.model.JobInstance apiInstance,
      Map<String, String> parameters,
      List<StepDetail> managerSteps,
      List<StepDetail> partitionSteps,
      boolean hasPartitions,
      long totalRead,
      long totalWrite,
      long totalSkip) {}

  @Mapping(target = "execution", source = "apiExecution")
  @Mapping(target = "jobInstance", source = "apiInstance")
  @Mapping(target = "parameters", source = "parameters")
  @Mapping(target = "managerSteps", source = "managerSteps")
  @Mapping(target = "partitionSteps", source = "partitionSteps")
  @Mapping(target = "hasPartitions", source = "hasPartitions")
  @Mapping(target = "totalRead", source = "totalRead")
  @Mapping(target = "totalWrite", source = "totalWrite")
  @Mapping(target = "totalSkip", source = "totalSkip")
  ExecutionDetail toExecutionDetail(ExecutionDetailContext context);

  @Mapping(target = "id", source = "instanceId")
  @Mapping(target = "version", source = "version")
  @Mapping(target = "jobName", source = "jobName")
  com.example.payments.export.model.JobInstance toJobInstance(JobInstance jobInstance);

  @Mapping(target = "status", expression = "java(stepExecution.getStatus().name())")
  @Mapping(target = "startTime", source = "startTime", qualifiedByName = "toOffsetDateTime")
  @Mapping(target = "endTime", source = "endTime", qualifiedByName = "toOffsetDateTime")
  @Mapping(target = "exitCode", source = "exitStatus.exitCode")
  @Mapping(target = "skipCount",
      expression = "java(stepExecution.getReadSkipCount() + stepExecution.getWriteSkipCount() + stepExecution.getProcessSkipCount())")
  @Mapping(target = "context", expression = "java(mapContext(stepExecution.getExecutionContext()))")
  @Mapping(target = "durationSeconds",
      expression = "java(calculateDuration(stepExecution.getStartTime(), stepExecution.getEndTime()))")
  StepDetail toStepDetail(StepExecution stepExecution);

  @Named("toOffsetDateTime")
  default OffsetDateTime toOffsetDateTime(LocalDateTime localDateTime) {
    if (localDateTime == null) {
      return null;
    }
    return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }

  default Map<String, String> mapContext(
      org.springframework.batch.item.ExecutionContext executionContext) {
    if (executionContext == null) {
      return new HashMap<>();
    }
    Map<String, String> result = new HashMap<>();
    executionContext.entrySet().forEach(e -> result.put(e.getKey(), String.valueOf(e.getValue())));
    return result;
  }

  default long calculateDuration(LocalDateTime startTime, LocalDateTime endTime) {
    if (startTime == null || endTime == null) {
      return 0;
    }
    return Duration.between(startTime, endTime).getSeconds();
  }
}
