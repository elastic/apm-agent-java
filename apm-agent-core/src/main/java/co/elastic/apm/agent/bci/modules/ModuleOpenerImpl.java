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

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused") //initialized via reflection
class ModuleOpenerImpl extends ModuleOpener {

    private static final Logger logger = LoggerFactory.getLogger(ModuleOpenerImpl.class);

    @IgnoreJRERequirement
    public boolean openModuleTo(Instrumentation instrumentation, Class<?> classFromTargetModule, ClassLoader openTo, Collection<String> packagesToOpen) {
        Module targetModule = classFromTargetModule.getModule();
        Module openToModule = openTo.getUnnamedModule();
        Set<Module> openToModuleSet = Collections.singleton(openToModule);
        Map<String, Set<Module>> missingOpens = new HashMap<>();
        for (String packageName : packagesToOpen) {
            if (!targetModule.isOpen(packageName, openToModule)) {
                missingOpens.put(packageName, openToModuleSet);
            }
        }
        if (!missingOpens.isEmpty()) {
            if (instrumentation.isModifiableModule(targetModule)) {
                logger.debug("Opening packages {} from module {} for instrumentation to module {}", missingOpens.keySet(), targetModule, openToModule);
                instrumentation.redefineModule(targetModule,
                    Collections.<Module>emptySet(), //reads
                    Collections.<String, Set<Module>>emptyMap(), //exports
                    missingOpens,  //opens
                    Collections.<Class<?>>emptySet(), //uses
                    Collections.<Class<?>, List<Class<?>>>emptyMap() //provides
                );
            } else {
                logger.error("Cannot open packages {} from module {} for instrumentation because module cannot be redefined!", missingOpens.keySet(), targetModule);
                return false;
            }
        }
        return true;
    }
}
