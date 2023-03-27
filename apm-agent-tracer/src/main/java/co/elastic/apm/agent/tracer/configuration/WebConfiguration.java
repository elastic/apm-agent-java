package co.elastic.apm.agent.tracer.configuration;

import java.util.List;

public interface WebConfiguration {

    List<Matcher> getIgnoreUrls();

    List<Matcher> getIgnoreUserAgents();

    List<Matcher> getUrlGroups();

    List<Matcher> getCaptureContentTypes();

    boolean isUsePathAsName();
}
