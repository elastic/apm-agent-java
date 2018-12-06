/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.servlet.wildfly;

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;

/**
 * Makes the {@code co.elastic.apm} package visible from all modules
 */
public class WildFlyLifecycleListener implements LifecycleListener {

    private static final String JBOSS_MODULES_SYSTEM_PKGS = "jboss.modules.system.pkgs";
    private static final String APM_BASE_PACKAGE = "co.elastic.apm";

    @Override
    public void start(ElasticApmTracer tracer) {
        final String systemPackages = System.getProperty(JBOSS_MODULES_SYSTEM_PKGS);
        if (systemPackages != null) {
            System.setProperty(JBOSS_MODULES_SYSTEM_PKGS, systemPackages + "," + APM_BASE_PACKAGE);
        } else {
            System.setProperty(JBOSS_MODULES_SYSTEM_PKGS, APM_BASE_PACKAGE);
        }
    }

    @Override
    public void stop() {
        // noop
    }
}
