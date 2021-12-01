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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;

public class Faas implements Recyclable {

    @Nullable
    private String execution;

    private boolean coldStart;

    private final FaasTrigger trigger = new FaasTrigger();

    @Nullable
    public String getExecution() {
        return execution;
    }

    public boolean isColdStart() {
        return coldStart;
    }

    public FaasTrigger getTrigger() {
        return trigger;
    }

    public Faas withExecution(@Nullable String execution) {
        this.execution = execution;
        return this;
    }

    public Faas withColdStart(boolean coldStart) {
        this.coldStart = coldStart;
        return this;
    }

    @Override
    public void resetState() {
        this.execution = null;
        this.coldStart = false;
        this.trigger.resetState();
    }

    public void copyFrom(Faas other) {
        this.execution = other.execution;
        this.coldStart = other.coldStart;
        this.trigger.copyFrom(other.trigger);
    }

    public boolean hasContent() {
        return execution != null ||
                coldStart ||
                trigger.hasContent();
    }
}
