package co.elastic.apm.agent.tracer;

import javax.annotation.Nullable;
import java.util.Set;

public interface Baggage {

    Set<String> keys();

    @Nullable
    String get(String key);
}
