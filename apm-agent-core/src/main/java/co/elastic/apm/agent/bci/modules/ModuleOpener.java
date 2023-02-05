/*
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
 */
package co.elastic.apm.agent.bci.modules;

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import javax.annotation.Nullable;
import java.lang.instrument.Instrumentation;
import java.util.Collection;

@IgnoreJRERequirement
public abstract class ModuleOpener {

    private static final Logger logger = LoggerFactory.getLogger(ModuleOpener.class);
    @Nullable
    private static ModuleOpener instance;

    private static final String IMPL_NAME = "co.elastic.apm.agent.bci.modules.ModuleOpenerImpl";

    public static boolean areModulesSupported() {
        return JvmRuntimeInfo.ofCurrentVM().getMajorVersion() >= 9;
    }

    public abstract boolean openModuleTo(Instrumentation instrumentation, Class<?> classFromTargetModule, ClassLoader openTo, Collection<String> packagesToOpen);

    public static ModuleOpener getInstance() {
        if (instance == null) {
            synchronized (ModuleOpener.class) {
                if (instance == null) {
                    if (areModulesSupported()) {
                        try {
                            instance = (ModuleOpener) Class.forName(IMPL_NAME).getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            logger.error("Failed to initialize ModuleOpener", e);
                            instance = new NoOp();
                        }
                    } else {
                        instance = new NoOp();
                    }
                }
            }
        }
        return instance;
    }

    private static class NoOp extends ModuleOpener {

        @Override
        public boolean openModuleTo(Instrumentation instrumentation, Class<?> classFromTargetModule, ClassLoader openTo, Collection<String> packagesToOpen) {
            return true;
        }
    }
}
