/*
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
 */
package co.elastic.apm.agent.quartzjob;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;
import org.quartz.jobs.DirectoryScanJob;
import org.quartz.jobs.DirectoryScanListener;
import org.springframework.scheduling.quartz.QuartzJobBean;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class Quartz2JobTransactionNameInstrumentationTest extends AbstractJobTransactionNameInstrumentationTest {

    @Override
    public TransactionImpl verifyTransactionFromJobDetails(JobDetail job, Outcome expectedOutcome) {
        reporter.awaitTransactionCount(1);

        TransactionImpl transaction = reporter.getFirstTransaction();
        await().untilAsserted(() -> assertThat(reporter.getTransactions().size()).isEqualTo(1));

        verifyTransaction(transaction, String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));

        assertThat(transaction.getOutcome()).isEqualTo(expectedOutcome);
        return transaction;
    }

    @Override
    public SimpleTrigger createTrigger() {
        return TriggerBuilder.newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0).withIntervalInMilliseconds(100))
            .build();
    }

    @Override
    String quartzVersion() {
        return "2.3.1";
    }

    @Override
    JobDetail buildJobDetailTestJob(String name, @Nullable String groupName) {
        if (groupName == null) {
            return buildJobDetail(TestJob.class, name);
        }
        return buildJobDetail(TestJob.class, name, groupName);
    }

    @Override
    void executeTestJobCreatingSpan(ElasticApmTracer tracer, boolean traced) throws JobExecutionException {
        new TestJobCreatingSpan(tracer, traced).execute(null);
    }

    @Override
    JobDetail buildJobDetailTestJobWithResult(String name) {
        return buildJobDetail(TestJobWithResult.class, name);
    }

    @Override
    JobDetail buildJobDetailTestJobWithException(String name) {
        return buildJobDetail(TestJobWithException.class, name);
    }

    private JobDetail buildJobDetail(Class jobClass, String name) {
        return JobBuilder.newJob(jobClass)
            .withIdentity(name)
            .build();
    }

    private JobDetail buildJobDetail(Class jobClass, String name, @Nullable String groupName) {
        if (groupName == null) {
            return buildJobDetail(jobClass, name);
        }
        return JobBuilder.newJob(jobClass)
            .withIdentity(name, groupName)
            .build();
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
            TransactionImpl transaction = tracer.currentTransaction();
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

    public static class TestJobWithException implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            throw new JobExecutionException("intentional job exception");
        }
    }

    public static class TestSpringJob extends QuartzJobBean {

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        }
    }

    @Test
    void testSpringJob() throws SchedulerException {
        JobDetail job = buildJobDetail(TestSpringJob.class, "dummyJobName", "group1");
        scheduler.scheduleJob(job, createTrigger());

        verifyTransactionFromJobDetails(job, Outcome.SUCCESS);
    }

    @Test
    void testDirectoryScan() throws SchedulerException, IOException {
        Path directoryScanTest = Files.createTempDirectory("DirectoryScanTest");

        final JobDetail job = JobBuilder.newJob(DirectoryScanJob.class)
            .withIdentity("dummyJobName")
            .usingJobData(DirectoryScanJob.DIRECTORY_NAME, directoryScanTest.toAbsolutePath().toString())
            .usingJobData(DirectoryScanJob.DIRECTORY_SCAN_LISTENER_NAME, TestDirectoryScanListener.class.getSimpleName())
            .build();

        scheduler.getContext().put(TestDirectoryScanListener.class.getSimpleName(), new TestDirectoryScanListener());
        scheduler.scheduleJob(job, createTrigger());

        verifyTransactionFromJobDetails(job, Outcome.SUCCESS);
    }

    public static class TestDirectoryScanListener implements DirectoryScanListener {

        @Override
        public void filesUpdatedOrAdded(File[] files) {
        }
    }

}
