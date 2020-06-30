/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.quartz.job;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.jobs.DirectoryScanJob;
import org.quartz.jobs.DirectoryScanListener;
import org.springframework.scheduling.quartz.QuartzJobBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


class JobTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    private Scheduler scheduler;

    @BeforeEach
    private void prepare() throws SchedulerException {
        scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
    }

    @AfterEach
    private void cleanup() throws SchedulerException {
        scheduler.shutdown();
        reporter.assertRecycledAfterDecrementingReferences();
    }

    void verifyJobDetails(JobDetail job) {
        await().untilAsserted(() -> assertThat(reporter.getTransactions().size()).isEqualTo(1));
        assertThat(reporter.getTransactions().get(0).getType()).isEqualToIgnoringCase("scheduled");
        assertThat(reporter.getTransactions().get(0).getNameAsString())
            .isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
        assertThat(reporter.getTransactions().get(0).getFrameworkName()).isEqualTo("Quartz");
        assertThat(reporter.getTransactions().get(0).getFrameworkVersion()).isEqualTo("2.3.1");
    }

    @Test
    void testJobWithGroup() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(TestJob.class)
            .withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        verifyJobDetails(job);
    }

    @Test
    void testJobWithoutGroup() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(TestJob.class)
            .withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        verifyJobDetails(job);
    }

    @Test
    void testJobManualCall() throws SchedulerException, InterruptedException {
        new TestJobCreatingSpan(tracer, true).execute(null);
        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        Transaction transaction = reporter.getTransactions().get(0);
        assertThat(transaction.getNameAsString()).isEqualToIgnoringCase("TestJobCreatingSpan#execute");
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        assertThat(reporter.getSpans().get(0).getTraceContext().getParentId()).isEqualTo(transaction.getTraceContext().getId());
    }

    @Test
    public void testAgentPaused() throws SchedulerException {
        TracerInternalApiUtils.pauseTracer(tracer);
        int transactionCount = objectPoolFactory.getTransactionPool().getRequestedObjectCount();
        int spanCount = objectPoolFactory.getSpanPool().getRequestedObjectCount();

        new TestJobCreatingSpan(tracer, false).execute(null);

        assertThat(reporter.getTransactions()).isEmpty();
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(objectPoolFactory.getTransactionPool().getRequestedObjectCount()).isEqualTo(transactionCount);
        assertThat(objectPoolFactory.getSpanPool().getRequestedObjectCount()).isEqualTo(spanCount);
    }

    @Test
    void testSpringJob() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(TestSpringJob.class)
            .withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        verifyJobDetails(job);
    }

    @Test
    void testJobWithResult() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(TestJobWithResult.class)
            .withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        verifyJobDetails(job);
        assertThat(reporter.getTransactions().get(0).getResult()).isEqualTo("this is the result");
    }

    @Test
    void testDirectoryScan() throws SchedulerException, IOException {
        Path directoryScanTest = Files.createTempDirectory("DirectoryScanTest");

        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        final JobDetail job = JobBuilder.newJob(DirectoryScanJob.class)
            .withIdentity("dummyJobName")
            .usingJobData(DirectoryScanJob.DIRECTORY_NAME, directoryScanTest.toAbsolutePath().toString())
            .usingJobData(DirectoryScanJob.DIRECTORY_SCAN_LISTENER_NAME, TestDirectoryScanListener.class.getSimpleName())
            .build();

        scheduler.getContext().put(TestDirectoryScanListener.class.getSimpleName(), new TestDirectoryScanListener());
        scheduler.scheduleJob(job, trigger);
        verifyJobDetails(job);
    }

    public static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
        }
    }

    public static class TestJobCreatingSpan implements Job {
        private final ElasticApmTracer tracer;
        private final boolean traced;

        public TestJobCreatingSpan(ElasticApmTracer tracer, boolean traced) {
            this.tracer = tracer;
            this.traced = traced;
        }

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            Transaction transaction = tracer.currentTransaction();
            if (traced) {
                assertThat(transaction).isNotNull();
                transaction.createSpan().end();
            } else {
                assertThat(transaction).isNull();
                assertThat(tracer.getActive()).isNull();
            }
        }
    }

    public static class TestJobWithResult implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            context.setResult("this is the result");
        }
    }

    public static class TestSpringJob extends QuartzJobBean {

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        }

    }

    public static class TestDirectoryScanListener implements DirectoryScanListener {

        @Override
        public void filesUpdatedOrAdded(File[] files) {
        }
    }
}
