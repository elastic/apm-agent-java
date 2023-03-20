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
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SimpleTrigger;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class Quartz1JobTransactionNameInstrumentationTest extends AbstractJobTransactionNameInstrumentationTest {

    @Override
    public Transaction verifyTransactionFromJobDetails(JobDetail job, Outcome expectedOutcome) {
        reporter.awaitTransactionCount(1, 2000);

        Transaction transaction = reporter.getFirstTransaction();
        await().untilAsserted(() -> assertThat(reporter.getTransactions().size()).isEqualTo(1));

        verifyTransaction(transaction, String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));

        assertThat(transaction.getOutcome()).isEqualTo(expectedOutcome);
        return transaction;
    }

    public SimpleTrigger createTrigger() {
        return new SimpleTrigger("myTrigger", 0, 100);
    }

    @Override
    public String quartzVersion() {
        return "1.7.3";
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

    @Override
    JobDetail buildJobDetailTestSpringJob(String name, String groupName) {
        throw new UnsupportedOperationException("Spring scheduling work with updated version of quartz.");
    }

    @Override
    boolean ignoreTestSpringJob() {
        // Spring scheduling work with updated version of quartz(2.*.*).
        return true;
    }

    @Override
    boolean ignoreDirectoryScanTest() {
        // DirectoryScanJob not present at this version
        return true;
    }

    private JobDetail buildJobDetail(Class jobClass, String name) {
        JobDetail job = new JobDetail();
        job.setName(name);
        job.setJobClass(jobClass);
        return job;
    }

    private JobDetail buildJobDetail(Class jobClass, String name, String groupName) {
        JobDetail job = new JobDetail();
        job.setName(name);
        job.setJobClass(jobClass);
        job.setGroup(groupName);
        return job;
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

    public static class TestJobWithException implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            throw new JobExecutionException("intentional job exception");
        }
    }


}
