/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

package co.elastic.apm.impl.error;

import co.elastic.apm.impl.transaction.TransactionId;
import co.elastic.apm.objectpool.Recyclable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Data for correlating errors with transactions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionReference implements Recyclable {

    /**
     * ID for the transaction
     */
    @JsonProperty("id")
    private final TransactionId id = new TransactionId();

    /**
     * UUID for the transaction
     */
    @JsonProperty("id")
    public TransactionId getId() {
        return id;
    }

    /**
     * UUID for the transaction
     */
    public TransactionReference withId(TransactionId id) {
        this.id.copyFrom(id);
        return this;
    }

    @Override
    public void resetState() {
        id.resetState();
    }
}
