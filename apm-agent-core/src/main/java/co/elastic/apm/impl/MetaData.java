package co.elastic.apm.impl;

import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;

public class MetaData {

    /**
     * Service
     * (Required)
     */
    protected final Service service;
    /**
     * Process
     */
    protected final ProcessInfo process;
    /**
     * System
     */
    protected final SystemInfo system;

    public MetaData(ProcessInfo process, Service service, SystemInfo system) {
        this.process = process;
        this.service = service;
        this.system = system;
    }

    /**
     * Service
     * (Required)
     *
     * @return the service name
     */
    public Service getService() {
        return service;
    }

    /**
     * Process
     *
     * @return the process name
     */
    public ProcessInfo getProcess() {
        return process;
    }

    /**
     * System
     *
     * @return the system name
     */
    public SystemInfo getSystem() {
        return system;
    }

}
