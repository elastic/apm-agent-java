/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.payload.ProcessFactory;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.ServiceFactory;
import co.elastic.apm.agent.impl.payload.SystemInfo;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;

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

    public static MetaData create(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName, @Nullable String frameworkVersion) {
        final Service service = new ServiceFactory().createService(configurationRegistry.getConfig(CoreConfiguration.class), frameworkName, frameworkVersion);
        final ProcessInfo processInformation = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        if (!configurationRegistry.getConfig(ReporterConfiguration.class).isIncludeProcessArguments()) {
            processInformation.getArgv().clear();
        }
        return new MetaData(processInformation, service, SystemInfo.create());
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
