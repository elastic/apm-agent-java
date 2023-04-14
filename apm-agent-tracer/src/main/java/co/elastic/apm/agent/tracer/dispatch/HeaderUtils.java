package co.elastic.apm.agent.tracer.dispatch;

import java.util.Set;

public class HeaderUtils {

    private HeaderUtils() {
    }

    public static <C> boolean containsAny(Set<String> headerNames, C carrier, TextHeaderGetter<C> headerGetter) {
        for (String headerName : headerNames) {
            if (headerGetter.getFirstHeader(headerName, carrier) != null) {
                return true;
            }
        }
        return false;
    }

    public static <S, D> void copy(Set<String> headerNames, S source, TextHeaderGetter<S> headerGetter, D destination, TextHeaderSetter<D> headerSetter) {
        for (String headerName : headerNames) {
            String headerValue = headerGetter.getFirstHeader(headerName, source);
            if (headerValue != null) {
                headerSetter.setHeader(headerName, headerValue, destination);
            }
        }
    }

    public static <C> void remove(Set<String> headerNames, C carrier, HeaderRemover<C> headerRemover) {
        for (String headerName : headerNames) {
            headerRemover.remove(headerName, carrier);
        }
    }
}
