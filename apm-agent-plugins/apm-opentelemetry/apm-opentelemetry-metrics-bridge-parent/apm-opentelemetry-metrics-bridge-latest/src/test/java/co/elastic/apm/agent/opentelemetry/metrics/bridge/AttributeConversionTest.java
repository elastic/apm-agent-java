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
package co.elastic.apm.agent.opentelemetry.metrics.bridge;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeConversionTest {

    @Test
    public void checkAllAttributeKeyTypesSupported() {
        BridgeFactoryLatest factory = new BridgeFactoryLatest();

        assertThat(factory.convertAttributeKey(AttributeKey.stringKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.doubleKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.longKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.booleanKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.stringArrayKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.doubleArrayKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.longArrayKey("key"))).isNotNull();
        assertThat(factory.convertAttributeKey(AttributeKey.booleanArrayKey("key"))).isNotNull();

        //we expect the tests above to be exhaustive
        assertThat(AttributeType.values()).hasSize(8);
    }

    @Test
    public void testAttributeKeyCaching() {
        BridgeFactoryLatest factory = new BridgeFactoryLatest();

        AttributeKey<?> key1 = AttributeKey.stringKey("a");
        AttributeKey<?> key2 = AttributeKey.booleanKey("a");

        Object converted1 = factory.convertAttributeKey(key1);
        Object converted2 = factory.convertAttributeKey(key2);

        assertThat(converted1).isNotNull()
            .isSameAs(factory.convertAttributeKey(key1));
        assertThat(converted2).isNotNull()
            .isNotSameAs(converted1)
            .isSameAs(factory.convertAttributeKey(key2));
    }


    @Test
    public void testAttributesCaching() {
        BridgeFactoryLatest factory = new BridgeFactoryLatest();

        AttributeKey<String> key1 = AttributeKey.stringKey("a");

        Attributes attrib1 = Attributes.of(key1, "foo");
        Attributes attrib2 = Attributes.of(key1, "foo");

        Object converted1 = factory.convertAttributes(attrib1);
        Object converted2 = factory.convertAttributes(attrib2);

        assertThat(converted1).isNotNull()
            .isSameAs(factory.convertAttributes(attrib1));
        assertThat(converted2).isNotNull()
            .isNotSameAs(converted1)
            .isSameAs(factory.convertAttributes(attrib2));
    }

}
