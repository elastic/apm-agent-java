
package co.elastic.apm.impl.error;

import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;


/**
 * Errors payload
 * <p>
 * List of errors wrapped in an object containing some other attributes normalized away from the errors themselves
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorPayload extends Payload {

    /**
     * (Required)
     */
    @JsonProperty("errors")
    private final List<ErrorCapture> errors = new ArrayList<ErrorCapture>();

    public ErrorPayload(ProcessInfo process, Service service, SystemInfo system) {
        super(process, service, system);
    }

    /**
     * (Required)
     */
    @JsonProperty("errors")
    public List<ErrorCapture> getErrors() {
        return errors;
    }

    @Override
    public List<? extends Recyclable> getPayloadObjects() {
        return errors;
    }

    @Override
    public void recycle() {
        for (ErrorCapture error : errors) {
            error.recycle();
        }
    }

    @Override
    public void resetState() {
        errors.clear();
    }

}
