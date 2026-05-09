package com.example.payments.export.web;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/batch")
@RequiredArgsConstructor
public class BatchDashboardController {

    private final JobExplorer jobExplorer;

    /**
     * Main dashboard: lists all jobs with their most recent execution status.
     */
    @GetMapping
    public String dashboard(Model model) {
        List<JobSummary> summaries = jobExplorer.getJobNames().stream()
                .map(this::buildJobSummary)
                .sorted(Comparator.comparing(JobSummary::getJobName))
                .collect(Collectors.toList());

        model.addAttribute("jobs", summaries);
        model.addAttribute("totalInstances", summaries.stream()
                .mapToLong(JobSummary::getInstanceCount).sum());
        model.addAttribute("totalExecutions", summaries.stream()
                .mapToInt(JobSummary::getTotalExecutions).sum());
        return "batch/dashboard";
    }

    /**
     * Job detail: all instances and executions for a single job name.
     */
    @GetMapping("/jobs/{jobName}")
    public String jobDetail(@PathVariable String jobName,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        int pageSize = 10;
        List<JobInstance> instances = new java.util.ArrayList<>();
        long totalInstances = 0;
        try {
            instances = jobExplorer.getJobInstances(jobName, page * pageSize, pageSize);
            totalInstances = jobExplorer.getJobInstanceCount(jobName);
        } catch (NoSuchJobException e) {
            // Job doesn't exist yet, show empty list
        }

        List<JobInstanceDetail> details = instances.stream()
                .map(instance -> buildInstanceDetail(instance, jobExplorer.getJobExecutions(instance)))
                .sorted(Comparator.comparingLong((JobInstanceDetail d) -> d.getInstanceId()).reversed())
                .collect(Collectors.toList());

        model.addAttribute("jobName", jobName);
        model.addAttribute("instances", details);
        model.addAttribute("page", page);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("totalPages", (int) Math.ceil((double) totalInstances / pageSize));
        model.addAttribute("totalInstances", totalInstances);
        return "batch/job-detail";
    }

    /**
     * Execution detail: step executions and partition breakdown for a single job execution.
     */
    @GetMapping("/executions/{executionId}")
    public String executionDetail(@PathVariable Long executionId, Model model) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            return "redirect:/batch";
        }

        List<StepDetail> steps = buildStepDetails(execution.getStepExecutions());

        // Separate partitioned steps: steps with ":" in name are sub-partitions
        List<StepDetail> managerSteps = steps.stream()
                .filter(s -> !s.getStepName().contains(":"))
                .collect(Collectors.toList());
        List<StepDetail> partitionSteps = steps.stream()
                .filter(s -> s.getStepName().contains(":"))
                .sorted(Comparator.comparing(StepDetail::getStepName))
                .collect(Collectors.toList());

        model.addAttribute("execution", execution);
        model.addAttribute("jobInstance", execution.getJobInstance());
        model.addAttribute("parameters", execution.getJobParameters().getParameters());
        model.addAttribute("managerSteps", managerSteps);
        model.addAttribute("partitionSteps", partitionSteps);
        model.addAttribute("hasPartitions", !partitionSteps.isEmpty());
        model.addAttribute("totalRead", partitionSteps.stream().mapToLong(StepDetail::getReadCount).sum());
        model.addAttribute("totalWrite", partitionSteps.stream().mapToLong(StepDetail::getWriteCount).sum());
        model.addAttribute("totalSkip", partitionSteps.stream().mapToLong(StepDetail::getSkipCount).sum());
        return "batch/execution-detail";
    }

    // ---- Internal builders ----

    private JobSummary buildJobSummary(String jobName) {
        long count = 0;
        List<JobInstance> recent = new java.util.ArrayList<>();
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
        return new JobSummary(jobName, count, lastStatus, totalExecutions);
    }

    private JobInstanceDetail buildInstanceDetail(JobInstance instance, List<JobExecution> executions) {
        String latestStatus = executions.isEmpty() ? "UNKNOWN"
                : executions.get(0).getStatus().name();
        return new JobInstanceDetail(
                instance.getId(),
                instance.getJobName(),
                latestStatus,
                executions
        );
    }

    private List<StepDetail> buildStepDetails(Collection<StepExecution> stepExecutions) {
        List<StepDetail> list = new ArrayList<>();
        for (StepExecution se : stepExecutions) {
            list.add(new StepDetail(
                    se.getStepName(),
                    se.getStatus().name(),
                    se.getExitStatus().getExitCode(),
                    se.getReadCount(),
                    se.getWriteCount(),
                    se.getFilterCount(),
                    se.getReadSkipCount() + se.getWriteSkipCount() + se.getProcessSkipCount(),
                    se.getCommitCount(),
                    se.getRollbackCount(),
                    se.getStartTime(),
                    se.getEndTime(),
                    se.getExecutionContext().entrySet().stream()
                            .collect(Collectors.toMap(
                                    e -> e.getKey(),
                                    e -> String.valueOf(e.getValue())
                            ))
            ));
        }
        return list;
    }

    // ---- View model records ----

    public record JobSummary(String jobName, long instanceCount, String lastStatus, int totalExecutions) {
        public long getInstanceCount() { return instanceCount; }
        public int getTotalExecutions() { return totalExecutions; }
        public String getJobName() { return jobName; }
        public String getLastStatus() { return lastStatus; }
    }

    public record JobInstanceDetail(Long instanceId, String jobName, String latestStatus, List<JobExecution> executions) {
        public Long getInstanceId() { return instanceId; }
        public String getJobName() { return jobName; }
        public String getLatestStatus() { return latestStatus; }
        public List<JobExecution> getExecutions() { return executions; }
    }

    public record StepDetail(
            String stepName,
            String status,
            String exitCode,
            long readCount,
            long writeCount,
            long filterCount,
            long skipCount,
            long commitCount,
            long rollbackCount,
            java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime,
            java.util.Map<String, String> context
    ) {
        public String getStepName() { return stepName; }
        public String getStatus() { return status; }
        public String getExitCode() { return exitCode; }
        public long getReadCount() { return readCount; }
        public long getWriteCount() { return writeCount; }
        public long getFilterCount() { return filterCount; }
        public long getSkipCount() { return skipCount; }
        public long getCommitCount() { return commitCount; }
        public long getRollbackCount() { return rollbackCount; }
        public java.time.LocalDateTime getStartTime() { return startTime; }
        public java.time.LocalDateTime getEndTime() { return endTime; }
        public java.util.Map<String, String> getContext() { return context; }

        public long getDurationSeconds() {
            if (startTime == null || endTime == null) return 0;
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }
}
