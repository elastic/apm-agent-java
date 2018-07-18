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

import co.elastic.apm.TransactionUtils;

import org.junit.jupiter.api.Test;

import static co.elastic.apm.JsonUtils.toJson;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @Test
    void resetState() {
        final Transaction transaction = new Transaction();
        TransactionUtils.fillTransaction(transaction);
        transaction.resetState();
        assertThat(toJson(transaction)).isEqualTo(toJson(new Transaction()));
    }
}
