package com.example.payments.export.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;

class ExportJobLauncherTest {

    @Test
    void testLaunchJob() throws Exception {
        JobLauncher launcher = mock(JobLauncher.class);
        Job job = mock(Job.class);

        ExportJobLauncher exportLauncher = new ExportJobLauncher(launcher, job);
        exportLauncher.launchJob();

        verify(launcher).run(any(), any());
    }

    @Test
    void testLaunchJobException() throws Exception {
        JobLauncher launcher = mock(JobLauncher.class);
        Job job = mock(Job.class);
        when(launcher.run(any(), any())).thenThrow(new RuntimeException("failed"));

        ExportJobLauncher exportLauncher = new ExportJobLauncher(launcher, job);
        exportLauncher.launchJob();

        verify(launcher).run(any(), any());
    }
}
