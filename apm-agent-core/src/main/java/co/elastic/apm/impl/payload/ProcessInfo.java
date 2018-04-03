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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;


/**
 * Process
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessInfo {

    /**
     * Process ID of the service
     * (Required)
     */
    @JsonProperty("pid")
    private long pid;
    /**
     * Parent process ID of the service
     */
    @Nullable
    @JsonProperty("ppid")
    private Long ppid;
    @JsonProperty("title")
    private final String title;
    /**
     * Command line arguments used to start this process
     */
    @JsonProperty("argv")
    private List<String> argv = new ArrayList<String>();

    public ProcessInfo(String title) {
        this.title = title;
    }

    /**
     * Process ID of the service
     * (Required)
     */
    @JsonProperty("pid")
    public long getPid() {
        return pid;
    }

    /**
     * Process ID of the service
     */
    public ProcessInfo withPid(long pid) {
        this.pid = pid;
        return this;
    }

    /**
     * Parent process ID of the service
     */
    @Nullable
    @JsonProperty("ppid")
    public Long getPpid() {
        return ppid;
    }

    /**
     * Parent process ID of the service
     */
    public ProcessInfo withPpid(Long ppid) {
        this.ppid = ppid;
        return this;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    /**
     * Command line arguments used to start this process
     */
    @JsonProperty("argv")
    public List<String> getArgv() {
        return argv;
    }

    /**
     * Command line arguments used to start this process
     */
    public ProcessInfo withArgv(List<String> argv) {
        this.argv = argv;
        return this;
    }

}
