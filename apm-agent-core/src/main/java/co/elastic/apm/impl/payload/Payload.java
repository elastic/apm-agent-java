/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.payload;

import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public abstract class Payload implements Recyclable {
    /**
     * Service
     * (Required)
     */
    @JsonProperty("service")
    protected final Service service;
    /**
     * Process
     */
    @JsonProperty("process")
    protected final ProcessInfo process;
    /**
     * System
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
     * (Required)
     *
     * @return the service name
     */
    @JsonProperty("service")
    public Service getService() {
        return service;
    }

    /**
     * Process
     *
     * @return the process name
     */
    @JsonProperty("process")
    public ProcessInfo getProcess() {
        return process;
    }

    /**
     * System
     *
     * @return the system name
     */
    @JsonProperty("system")
    public SystemInfo getSystem() {
        return system;
    }

    @JsonIgnore
    public abstract List<? extends Recyclable> getPayloadObjects();

    public abstract void recycle();
}
