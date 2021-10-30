package co.elastic.apm.agent.quartzjob;

import org.quartz.JobExecutionContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Quartz1JobExecutionContextHandler implements JobExecutionContextHandler<JobExecutionContext> {
    @Override
    @Nullable
    public String getJobDetailKey(@Nonnull JobExecutionContext jobExecutionContext) {
        if (jobExecutionContext.getJobDetail() == null) {
            return null;
        }
        return jobExecutionContext.getJobDetail().getKey().toString();
    }

    @Override
    @Nullable
    public Object getResult(@Nonnull JobExecutionContext jobExecutionContext) {
        return jobExecutionContext.getResult();
    }
}
