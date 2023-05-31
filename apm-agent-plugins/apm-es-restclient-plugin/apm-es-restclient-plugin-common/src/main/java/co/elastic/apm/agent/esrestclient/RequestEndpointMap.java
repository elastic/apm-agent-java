package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;

@GlobalState
public class RequestEndpointMap {
    public static final WeakMap<Object, String> requestEndpointIdMap = WeakConcurrent.buildMap();
}
