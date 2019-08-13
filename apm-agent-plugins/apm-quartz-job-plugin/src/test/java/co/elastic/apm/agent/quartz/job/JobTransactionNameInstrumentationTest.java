/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;


class JobTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    private static Scheduler scheduler;
    private static CompletableFuture<Boolean> responseFuture;

    @BeforeAll
    private static void setup() throws SchedulerException {
        scheduler = new StdSchedulerFactory().getScheduler();
    }

    @AfterAll
    private static void cleanup() throws SchedulerException {
        scheduler.shutdown();
    }

    @BeforeEach
    private void prepare() throws SchedulerException {
        responseFuture = new CompletableFuture<>();
    }

    void verifyJobDetails(JobDetail job) throws InterruptedException {
        reporter.getFirstTransaction(150);
        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getType()).isEqualToIgnoringCase("scheduled");
        assertThat(reporter.getTransactions().get(0).getNameAsString())
            .isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
    }

    @Test
    void testJobWithGroup() throws SchedulerException, InterruptedException, ExecutionException {
        JobDetail job = JobBuilder.newJob(TestJob.class)
            .withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        responseFuture.get();
        scheduler.deleteJob(job.getKey());
        verifyJobDetails(job);
    }

    @Test
    void testJobWithoutGroup() throws SchedulerException, InterruptedException, ExecutionException {
        JobDetail job = JobBuilder.newJob(TestJob.class)
            .withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        responseFuture.get();
        scheduler.deleteJob(job.getKey());
        verifyJobDetails(job);
    }

    @Test
    void testJobManualCall() throws SchedulerException, InterruptedException {
        TestJob job = new TestJob();
        job.execute(null);
        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualToIgnoringCase("TestJob#execute");
    }

    @Test
    void testSpringJob() throws SchedulerException, InterruptedException, ExecutionException {
        JobDetail job = JobBuilder.newJob(TestSpringJob.class)
            .withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        responseFuture.get();
        scheduler.deleteJob(job.getKey());
        verifyJobDetails(job);
    }

    @Test
    void testJobWithResult() throws SchedulerException, InterruptedException, ExecutionException {
        JobDetail job = JobBuilder.newJob(TestJobWithResult.class)
            .withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        responseFuture.get();
        scheduler.deleteJob(job.getKey());
        verifyJobDetails(job);
        assertThat(reporter.getTransactions().get(0).getResult()).isEqualTo("this is the result");
    }

    public static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            responseFuture.complete(Boolean.TRUE);
        }
    }

    public static class TestJobWithResult implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            context.setResult("this is the result");
            responseFuture.complete(Boolean.TRUE);
        }
    }

    public static class TestSpringJob extends QuartzJobBean {

        @Override
        protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
            responseFuture.complete(Boolean.TRUE);
        }

    }
}
