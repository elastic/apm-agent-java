package co.elastic.apm.agent.quartzjob;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface JobExecutionContextHandler<T> {

    @Nullable
    String getJobDetailKey(@Nonnull T jobExecutionContext);

    @Nullable
    Object getResult(@Nonnull T jobExecutionContext);
}
