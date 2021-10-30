package co.elastic.apm.agent.quartzjob;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


class Quartz1JobTransactionNameInstrumentationTest extends AbstractJobTransactionNameInstrumentationTest {

    @Override
    public Transaction verifyTransactionFromJobDetails(JobDetail job, Outcome expectedOutcome) {
        reporter.awaitTransactionCount(1);

        Transaction transaction = reporter.getFirstTransaction();
        await().untilAsserted(() -> assertThat(reporter.getTransactions().size()).isEqualTo(1));

        verifyTransaction(transaction, String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));

        assertThat(transaction.getOutcome()).isEqualTo(expectedOutcome);
        return transaction;
    }

    public SimpleTrigger createTrigger() {
        return new SimpleTrigger("myTrigger", 0, 1);
    }

    @Override
    String quartzVersion() {
        return "1.7.3";
    }

    @Override
    JobDetail buildJobDetail(Class jobClass, String name) {
        JobDetail job = new JobDetail();
        job.setName(name);
        job.setJobClass(jobClass);
        return job;
    }

    @Override
    JobDetail buildJobDetail(Class jobClass, String name, String groupName) {
        JobDetail job = buildJobDetail(jobClass, name);
        job.setGroup(groupName);
        return job;
    }
}
