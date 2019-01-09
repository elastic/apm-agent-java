/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.objectpool.Recyclable;

import java.util.concurrent.atomic.AtomicInteger;

public class SpanCount implements Recyclable {

    private final AtomicInteger dropped = new AtomicInteger(0);
    private final AtomicInteger started = new AtomicInteger(0);

    public AtomicInteger getDropped() {
        return dropped;
    }

    public AtomicInteger getStarted() {
        return started;
    }

    @Override
    public void resetState() {
        dropped.set(0);
        started.set(0);
    }
}
