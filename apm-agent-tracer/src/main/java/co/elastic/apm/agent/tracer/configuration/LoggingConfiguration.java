package co.elastic.apm.agent.tracer.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface LoggingConfiguration {
    Map<String, String> getLogEcsReformattingAdditionalFields();

    LogEcsReformatting getLogEcsReformatting();

    boolean getSendLogs();

    @Nullable
    String getLogEcsFormattingDestinationDir();

    List<WildcardMatcher> getLogEcsFormatterAllowList();

    long getLogFileSize();

    long getDefaultLogFileSize();
}
