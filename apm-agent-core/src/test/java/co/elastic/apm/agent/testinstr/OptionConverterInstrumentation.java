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
package co.elastic.apm.agent.testinstr;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link org.apache.log4j.helpers.OptionConverter} to make {@link org.apache.log4j.helpers.Loader} work as
 * expected on Java 17+. As log4j1 is now EOL http://logging.apache.org/log4j/1.2/ it's the best way to keep our tests
 * active and relevant on this feature.
 */
public class OptionConverterInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.log4j.helpers.OptionConverter");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getSystemProperty");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.emptyList();
    }

    public static class AdviceClass {

        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static String onExit(@Advice.Argument(0) String key,
                                    @Advice.Return @Nullable String returnValue) {

            if (returnValue == null || !"java.version".equals(key)) {
                return returnValue;
            }

            if (returnValue.indexOf('.') < 0) {
                // just convert '17' to '17.0' to make Log4j simple version parsing work and not assume Java 1.x
                return returnValue + ".0";
            } else {
                return returnValue;
            }
        }
    }
}
