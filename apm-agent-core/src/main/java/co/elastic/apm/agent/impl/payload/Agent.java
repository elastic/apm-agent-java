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

package co.elastic.apm.agent.impl.payload;


import java.util.UUID;

/**
 * Name and version of the Elastic APM agent
 */
public class Agent {

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    private final String name;
    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    private final String version;

    /**
     * A unique agent ID, non-persistent (i.e. changes on restart).
     * <a href="https://www.elastic.co/guide/en/ecs/master/ecs-agent.html#_agent_field_details">See ECS for reference</a>.
     */
    private final String ephemeralId;

    public Agent(String name, String version) {
        this(name, version, UUID.randomUUID().toString());
    }

    public Agent(String name, String version, String ephemeralId) {
        this.name = name;
        this.version = version;
        this.ephemeralId = ephemeralId;
    }

    /**
     * Name of the Elastic APM agent, e.g. "Python"
     * (Required)
     */
    public String getName() {
        return name;
    }

    /**
     * Version of the Elastic APM agent, e.g."1.0.0"
     * (Required)
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return A unique agent ID, non-persistent (i.e. changes on restart).
     */
    public String getEphemeralId() {
        return ephemeralId;
    }
}
