/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;

public class SpanCount implements Recyclable {

    private final Dropped dropped = new Dropped();
    private int total = 0;

    public Dropped getDropped() {
        return dropped;
    }

    public void increment() {
        total++;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public void resetState() {
        dropped.resetState();
        total = 0;
    }
}
