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
import io.opentelemetry.api.common.Attributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class BridgeFactoryV1_14Test {

    @Test
    void checkAttributeCachesLimited() {
        List<Attributes> attributes = LongStream.range(0, 10_000)
            .mapToObj(i -> Attributes.of(AttributeKey.longKey("foo-" + i), i))
            .collect(Collectors.toList());

        BridgeFactoryV1_14 bridge = new BridgeFactoryV1_14();
        attributes.forEach(bridge::convertAttributes);

        assertThat(bridge.convertedAttributes.approximateSize()).isEqualTo(BridgeFactoryV1_14.MAX_ATTRIBUTE_CACHE_SIZE);
        assertThat(bridge.convertedAttributeKeys.approximateSize()).isEqualTo(BridgeFactoryV1_14.MAX_ATTRIBUTE_KEY_CACHE_SIZE);
    }

}
