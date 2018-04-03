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
package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class TransactionIdTest {

    @Test
    void toUuid() {
        TransactionId id = new TransactionId();
        id.setToRandomValue();
        UUID uuid = id.toUuid();
        assertThat(uuid.getMostSignificantBits()).isEqualTo(id.getMostSignificantBits());
        assertThat(uuid.getLeastSignificantBits()).isEqualTo(id.getLeastSignificantBits());
    }

    @Test
    void testCopy() {
        TransactionId id1 = new TransactionId();
        TransactionId id2 = new TransactionId();
        assertThat(id1.getLeastSignificantBits()).isEqualTo(0);
        assertThat(id1).isEqualTo(id2);
        id1.setToRandomValue();
        assertThat(id1).isNotEqualTo(id2);

        id2.copyFrom(id1);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.getLeastSignificantBits()).isNotEqualTo(0);
    }
}
