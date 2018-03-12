package co.elastic.apm.impl.payload;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public abstract class Payload implements Recyclable {
    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    protected final Service service;
    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    protected final ProcessInfo process;
    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    protected final SystemInfo system;

    public Payload(ProcessInfo process, Service service, SystemInfo system) {
        this.process = process;
        this.service = service;
        this.system = system;
    }

    /**
     * Service
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("service")
    public Service getService() {
        return service;
    }

    /**
     * Process
     * <p>
     */
    @JsonProperty("process")
    public ProcessInfo getProcess() {
        return process;
    }

    /**
     * System
     * <p>
     */
    @JsonProperty("system")
    public SystemInfo getSystem() {
        return system;
    }

    public abstract List<? extends Recyclable> getPayloadObjects();

    public abstract void recycle();
}
