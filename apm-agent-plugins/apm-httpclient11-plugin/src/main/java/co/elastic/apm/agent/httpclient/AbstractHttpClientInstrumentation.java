package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractHttpClientInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "httpclient11");
    }

}
