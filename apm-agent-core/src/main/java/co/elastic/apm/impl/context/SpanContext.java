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

package co.elastic.apm.impl.context;

import co.elastic.apm.impl.transaction.Db;


/**
 * Any other arbitrary data captured by the agent, optionally provided by the user
 */
public class SpanContext extends AbstractContext {

    /**
     * An object containing contextual data for database spans
     */
    private final Db db = new Db();

    /**
     * An object containing contextual data for database spans
     */
    public Db getDb() {
        return db;
    }

    @Override
    public void resetState() {
        super.resetState();
        db.resetState();
    }

    public boolean hasContent() {
        return super.hasContent() || db.hasContent();
    }
}
