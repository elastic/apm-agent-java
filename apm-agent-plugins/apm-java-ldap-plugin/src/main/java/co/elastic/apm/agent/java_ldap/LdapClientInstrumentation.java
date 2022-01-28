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
package co.elastic.apm.agent.java_ldap;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class LdapClientInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("com.sun.jndi.ldap.LdapClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("authenticate")
            .or(named("add"))
            .or(named("compare"))
            .or(named("delete"))
            .or(named("extendedOp"))
            .or(named("moddn"))
            .or(named("modify"))
            .or(named("search"));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.java_ldap.LdapClientAdvice";
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("java-ldap");
    }
}
