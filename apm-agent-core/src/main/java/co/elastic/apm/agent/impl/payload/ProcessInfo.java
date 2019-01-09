/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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

package co.elastic.apm.agent.impl.payload;


import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;


/**
 * Process
 */
public class ProcessInfo {

    /**
     * Process ID of the service
     * (Required)
     */
    private long pid;
    /**
     * Parent process ID of the service
     */
    @Nullable
    private Long ppid;
    private final String title;
    /**
     * Command line arguments used to start this process
     */
    private List<String> argv = new ArrayList<String>();

    public ProcessInfo(String title) {
        this.title = title;
    }

    /**
     * Process ID of the service
     * (Required)
     */
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
    public Long getPpid() {
        return ppid;
    }

    /**
     * Parent process ID of the service
     */
    public ProcessInfo withPpid(@Nullable Long ppid) {
        this.ppid = ppid;
        return this;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Command line arguments used to start this process
     */
    public List<String> getArgv() {
        return argv;
    }

    /**
     * Command line arguments used to start this process
     */
    public ProcessInfo withArgv(@Nullable List<String> argv) {
        if (argv != null) {
            this.argv.addAll(argv);
        }
        return this;
    }

}
