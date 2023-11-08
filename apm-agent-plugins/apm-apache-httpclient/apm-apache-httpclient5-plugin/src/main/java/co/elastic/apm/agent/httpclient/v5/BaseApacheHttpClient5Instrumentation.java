package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;

import java.util.Arrays;
import java.util.Collection;

public abstract class BaseApacheHttpClient5Instrumentation extends ElasticApmInstrumentation {

    static final Tracer tracer = GlobalTracer.get();

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "apache-httpclient");
    }
}
