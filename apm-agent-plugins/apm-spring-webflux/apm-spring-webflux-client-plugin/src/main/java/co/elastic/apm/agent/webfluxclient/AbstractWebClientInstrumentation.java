package co.elastic.apm.agent.webfluxclient;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractWebClientInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-webflux", "experimental");
    }
}
