package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;

import java.util.Arrays;
import java.util.Collection;

public abstract class BaseApacheHttpClient5Instrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client5", "apache-httpclient5");
    }
}
