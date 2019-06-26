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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.impl.StdSchedulerFactory;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.quartz.job.JobTransactionNameInstrumentation;
import co.elastic.apm.agent.report.ApmServerReporter;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.springframework.scheduling.quartz.QuartzJobBean;

import static org.assertj.core.api.Assertions.assertThat;


class JobTransactionNameInstrumentationTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;

    @BeforeClass
    @BeforeAll
    static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
                .configurationRegistry(SpyConfiguration.createSpyConfig())
                .reporter(reporter)
                .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
                Collections.singletonList(new JobTransactionNameInstrumentation(tracer)));
    }

    @Test
    void testJobWithGroup() throws SchedulerException, InterruptedException {
        reporter.reset();
        resetCounter();
        Scheduler scheduler=new StdSchedulerFactory().getScheduler();
        scheduler.clear();
        JobDetail job = JobBuilder.newJob(TestJob.class)
    			.withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
        		.newTrigger()
        		.withIdentity("myTrigger")
        		.withSchedule(
        				SimpleScheduleBuilder.repeatSecondlyForTotalCount(2, 1))
        		.build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        Thread.sleep(2500);
        scheduler.shutdown();
        assertThat(reporter.getTransactions().size()).isEqualTo(getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("scheduledTime")).asString().isNotEmpty();
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("trigger")).asString()
        	.isEqualToIgnoringCase(String.format("%s.%s", trigger.getKey().getGroup(), trigger.getKey().getName()));
        assertThat(reporter.getTransactions().get(0).getName())
        	.isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
    }

    @Test
    void testJobWithoutGroup() throws SchedulerException, InterruptedException {
        reporter.reset();
        resetCounter();
        Scheduler scheduler=new StdSchedulerFactory().getScheduler();
        scheduler.clear();
        JobDetail job = JobBuilder.newJob(TestJob.class)
    			.withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
        		.newTrigger()
        		.withIdentity("myTrigger")
        		.withSchedule(
        				SimpleScheduleBuilder.repeatSecondlyForTotalCount(2, 1))
        		.build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        Thread.sleep(2500);
        scheduler.shutdown();
        assertThat(reporter.getTransactions().size()).isEqualTo(getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("scheduledTime")).asString().isNotEmpty();
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("trigger")).asString()
        	.isEqualToIgnoringCase(String.format("%s.%s", trigger.getKey().getGroup(), trigger.getKey().getName()));
        assertThat(reporter.getTransactions().get(0).getName())
        	.isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
    }
    
    @Test
	void testJobManualCall() throws SchedulerException, InterruptedException {
        reporter.reset();
        resetCounter();
        TestJob job=new TestJob();
        job.execute(null);
        assertThat(reporter.getTransactions().size()).isEqualTo(getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getName()).isEqualToIgnoringCase("TestJob#execute");
    }

    @Test
    void testSpringJob() throws SchedulerException, InterruptedException {
        reporter.reset();
        resetCounter();
        Scheduler scheduler=new StdSchedulerFactory().getScheduler();
        scheduler.clear();
        JobDetail job = JobBuilder.newJob(TestSpringJob.class)
    			.withIdentity("dummyJobName", "group1").build();
        Trigger trigger = TriggerBuilder
        		.newTrigger()
        		.withIdentity("myTrigger")
        		.withSchedule(
        				SimpleScheduleBuilder.repeatSecondlyForTotalCount(2, 1))
        		.build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        Thread.sleep(2500);
        scheduler.shutdown();
        assertThat(reporter.getTransactions().size()).isEqualTo(getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("scheduledTime")).asString().isNotEmpty();
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("trigger")).asString()
        	.isEqualToIgnoringCase(String.format("%s.%s", trigger.getKey().getGroup(), trigger.getKey().getName()));
        assertThat(reporter.getTransactions().get(0).getName())
        	.isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
    }

    @Test
    void testJobWithResult() throws SchedulerException, InterruptedException {
    	reporter.reset();
        resetCounter();
        Scheduler scheduler=new StdSchedulerFactory().getScheduler();
        scheduler.clear();
        JobDetail job = JobBuilder.newJob(TestJobWithResult.class)
    			.withIdentity("dummyJobName").build();
        Trigger trigger = TriggerBuilder
        		.newTrigger()
        		.withIdentity("myTrigger")
        		.withSchedule(
        			SimpleScheduleBuilder.repeatSecondlyForTotalCount(2, 1))
        		.build();
        scheduler.scheduleJob(job, trigger);
        scheduler.start();
        Thread.sleep(2500);
        scheduler.shutdown();
        assertThat(reporter.getTransactions().size()).isEqualTo(getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getResult()).isEqualTo("this is the result");
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("scheduledTime")).asString().isNotEmpty();
        assertThat(reporter.getTransactions().get(0).getContext().getCustom("trigger")).asString()
        	.isEqualToIgnoringCase(String.format("%s.%s", trigger.getKey().getGroup(), trigger.getKey().getName()));
        assertThat(reporter.getTransactions().get(0).getName())
        	.isEqualToIgnoringCase(String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));
    }

    private static AtomicInteger count = new AtomicInteger(0);
    private static int getInvocationCount() {
        return count.get();
    }
    private static void resetCounter() {
    	count.set(0);
    }
    public static class TestJob implements Job {
		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			count.incrementAndGet();
		}
    }
    public static class TestJobWithResult implements Job {
		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			context.setResult("this is the result");
			count.incrementAndGet();
		}
    }
    public static class TestSpringJob extends QuartzJobBean {

		@Override
		protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
			count.incrementAndGet();
		}

    }
}
