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
package co.elastic.apm.agent.tracer;

public class EagerThrowable extends Throwable {

    private final Class<? extends Throwable> originalType;

    public EagerThrowable(Throwable t) {
        super(t.getMessage(), t.getCause(), true, false);
        setStackTrace(t.getStackTrace());
        this.originalType = t.getClass();
        Throwable[] suppressed = t.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            addSuppressed(suppressed[i]);
        }
    }

    public Class<? extends Throwable> getOriginalType() {
        return originalType;
    }

}
