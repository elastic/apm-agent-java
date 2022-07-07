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
package co.elastic.apm.agent.mongodb.v3;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionGte;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionLte;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;


public abstract class MongoClientInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
        // only use this instrumentation for 3.x
        return implementationVersionGte("3").and(not(implementationVersionGte("4")));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.mongodb.")
            .and(hasSuperType(named("com.mongodb.connection.Connection")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        // has already been published with 'mongodb-client', thus keeping it as an alias in case it has been disabled
        // on the java agent there is no ambiguity as mongodb will always be a mongodb client (server written in c++).
        return Arrays.asList("mongodb-client", "mongodb");
    }

}
