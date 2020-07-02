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
package co.elastic.apm.agent.grpc;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.TracerAwareElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.grpc.helper.GrpcHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

public abstract class BaseInstrumentation extends TracerAwareElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<GrpcHelper> grpcHelperManager;

    public BaseInstrumentation(ElasticApmTracer tracer) {
        synchronized (BaseInstrumentation.class) {
            // we need to make sure that only a single instance of helper class manager is created
            // otherwise 'static' fields semantics do not hold in helper class as multiple versions of helper class
            // are loaded in distinct classloaders.
            if (grpcHelperManager == null) {
                grpcHelperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                    "co.elastic.apm.agent.grpc.helper.GrpcHelperImpl",
                    "co.elastic.apm.agent.grpc.helper.GrpcHelperImpl$GrpcHeaderSetter",
                    "co.elastic.apm.agent.grpc.helper.GrpcHelperImpl$GrpcHeaderGetter");
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("io.grpc");
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(GrpcHelper.GRPC);
    }

}
