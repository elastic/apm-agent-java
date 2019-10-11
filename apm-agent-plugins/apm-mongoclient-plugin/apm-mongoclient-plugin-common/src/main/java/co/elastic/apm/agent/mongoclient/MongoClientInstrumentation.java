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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandListener;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

public abstract class MongoClientInstrumentation extends ElasticApmInstrumentation {

    @Nullable
    @VisibleForAdvice
    public static HelperClassManager<MongoClientInstrumentationHelper<CommandEvent, CommandListener>> mongoClientInstrHelperManager;

    public MongoClientInstrumentation(ElasticApmTracer tracer) {
        mongoClientInstrHelperManager = HelperClassManager.ForAnyClassLoader.of(tracer,
            "co.elastic.apm.agent.mongoclient.MongoClientInstrumentationHelperImpl",
            "co.elastic.apm.agent.mongoclient.CommandListenerWrapper",
            "co.elastic.apm.agent.mongoclient.MongoClientInstrumentationHelperImpl$CommandListenerAllocator");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("mongodb-client");
    }
}
