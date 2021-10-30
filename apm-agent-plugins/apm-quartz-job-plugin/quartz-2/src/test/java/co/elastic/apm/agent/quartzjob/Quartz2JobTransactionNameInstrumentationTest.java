package co.elastic.apm.agent.quartzjob;

import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class Quartz2JobTransactionNameInstrumentationTest extends AbstractJobTransactionNameInstrumentationTest {

    @Override
    public SimpleTrigger createTrigger() {
        return TriggerBuilder.newTrigger()
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.repeatSecondlyForTotalCount(1, 1))
            .build();
    }

    @Override
    String quartzVersion() {
        return "2.3.1";
    }

    @Override
    JobDetail buildJobDetail(Class jobClass, String name) {
        return JobBuilder.newJob(TestJob.class)
            .withIdentity(name)
            .build();
    }

    @Override
    JobDetail buildJobDetail(Class jobClass, String name, String groupName) {
        return JobBuilder.newJob(TestJob.class)
            .withIdentity(name, groupName)
            .build();
    }

    public Transaction verifyTransactionFromJobDetails(JobDetail job, Outcome expectedOutcome) {
        reporter.awaitTransactionCount(1);

        Transaction transaction = reporter.getFirstTransaction();
        await().untilAsserted(() -> assertThat(reporter.getTransactions().size()).isEqualTo(1));

        verifyTransaction(transaction, String.format("%s.%s", job.getKey().getGroup(), job.getKey().getName()));

        assertThat(transaction.getOutcome()).isEqualTo(expectedOutcome);
        return transaction;
    }
}
