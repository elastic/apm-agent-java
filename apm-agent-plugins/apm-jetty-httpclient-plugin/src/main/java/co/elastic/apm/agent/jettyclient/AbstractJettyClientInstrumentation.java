package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;

import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractJettyClientInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "jetty-client");
    }
}
