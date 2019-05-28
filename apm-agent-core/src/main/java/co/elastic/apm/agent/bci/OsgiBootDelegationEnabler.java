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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;

import javax.annotation.Nullable;

/**
 * Required in OSGi environments like Equinox, which is used in WebSphere.
 * By adding the base package of the APM agent,
 * the instrumented classes have access to the agent classes,
 * without specifying {@code Import-Package} bundle headers.
 * <p>
 * Note: in Apache Felix the boot delegation only works for classes loaded by the bootstrap classloader,
 * which means that the whole agent code needs to be added to the bootstrap classloader.
 * See {@link AgentMain#init(String, java.lang.instrument.Instrumentation)}
 * </p>
 */
public class OsgiBootDelegationEnabler implements LifecycleListener {
    private static final String APM_BASE_PACKAGE = "co.elastic.apm.agent.*";
    // see https://confluence.atlassian.com/jirakb/using-javaagent-with-jira-790793295.html#UsingjavaagentwithJIRA-Resolution
    private static final String ATLASSIAN_BOOTDELEGATION_DEFAULTS = "META-INF.services,com.yourkit,com.singularity.*,com.jprofiler," +
        "com.jprofiler.*,org.apache.xerces,org.apache.xerces.*,org.apache.xalan,org.apache.xalan.*,sun.*,com.sun.jndi.*,com.icl.saxon," +
        "com.icl.saxon.*,javax.servlet,javax.servlet.*,com.sun.xml.bind.*";

    @Override
    public void start(ElasticApmTracer tracer) {
        // may be problematic as it could override the defaults in a properties file
        String packagesToAppendToBootdelegationProperty = tracer.getConfig(CoreConfiguration.class).getPackagesToAppendToBootdelegationProperty();
        if (packagesToAppendToBootdelegationProperty != null) {
            appendToSystemProperty("org.osgi.framework.bootdelegation", packagesToAppendToBootdelegationProperty);
        }
        appendToSystemProperty("atlassian.org.osgi.framework.bootdelegation", ATLASSIAN_BOOTDELEGATION_DEFAULTS, APM_BASE_PACKAGE);
    }

    private static void appendToSystemProperty(String propertyName, String append) {
        appendToSystemProperty(propertyName, null, append);
    }

    private static void appendToSystemProperty(String propertyName, @Nullable String propertyValueDefault, String append) {
        final String systemPackages = System.getProperty(propertyName, propertyValueDefault);
        if (systemPackages != null) {
            System.setProperty(propertyName, systemPackages + "," + append);
        } else {
            System.setProperty(propertyName, append);
        }
    }

    @Override
    public void stop() {
        // noop
    }
}
