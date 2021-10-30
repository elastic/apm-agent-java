package co.elastic.apm.agent.quartzjob;

import org.quartz.JobExecutionContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Quartz2JobExecutionContextHandler implements JobExecutionContextHandler<JobExecutionContext> {
    @Override
    @Nullable
    public String getJobDetailKey(@Nonnull JobExecutionContext jobExecutionContext) {
        if (jobExecutionContext.getJobDetail() == null) {
            return null;
        }
        return jobExecutionContext.getJobDetail().getKey().toString();
    }

    @Nullable
    @Override
    public Object getResult(@Nullable JobExecutionContext jobExecutionContext) {
        return jobExecutionContext != null ? jobExecutionContext.getResult() : null;
    }
}
