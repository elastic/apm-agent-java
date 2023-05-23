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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

public class FaasTrigger implements Recyclable {

    @Nullable
    private String type;

    @Nullable
    private String requestId;

    @Nullable
    public String getType() {
        return type;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    public FaasTrigger withType(@Nullable String type) {
        this.type = type;
        return this;
    }

    public FaasTrigger withRequestId(@Nullable String requestId) {
        this.requestId = requestId;
        return this;
    }

    @Override
    public void resetState() {
        this.type = null;
        this.requestId = null;
    }

    public void copyFrom(FaasTrigger other) {
        this.type = other.type;
        this.requestId = other.requestId;
    }

    public boolean hasContent() {
        return type != null ||
                requestId != null;
    }
}
