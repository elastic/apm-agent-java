package co.elastic.apm.agent.tracer.configuration;

import java.util.List;

public interface ElasticsearchConfiguration {

    List<Matcher> getCaptureBodyUrls();
}
