/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.errorlogging;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class Slf4jLoggerErrorCapturingInstrumentation extends AbstractLoggerErrorCapturingInstrumentation {

    // prevents the shade plugin from relocating org.slf4j.Logger to co.elastic.apm.agent.shaded.slf4j.Logger
    static final String SLF4J_LOGGER = "org!slf4j!Logger".replace('!', '.');

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named(SLF4J_LOGGER)
            .and(not(hasSuperType(named(Log4j2LoggerErrorCapturingInstrumentation.LOG4J2_LOGGER)))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = super.getInstrumentationGroupNames();
        ret.add("slf4j");
        return ret;
    }
}
