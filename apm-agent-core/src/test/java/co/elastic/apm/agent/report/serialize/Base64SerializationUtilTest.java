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
package co.elastic.apm.agent.report.serialize;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class Base64SerializationUtilTest {

    @Test
    public void empty() {
        JsonWriter jw = new DslJson<>(new DslJson.Settings<>()).newWriter();
        Base64SerializationUtils.writeBytesAsBase64UrlSafe(new byte[0], jw);
        assertThat(jw.size()).isEqualTo(0);
    }

    @Test
    public void randomInputs() {
        DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

        Base64.Encoder reference = Base64.getUrlEncoder().withoutPadding();

        Random rnd = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            int len = rnd.nextInt(31) + 1;
            byte[] data = new byte[len];
            rnd.nextBytes(data);

            String expectedResult = reference.encodeToString(data);

            JsonWriter jw = dslJson.newWriter();
            Base64SerializationUtils.writeBytesAsBase64UrlSafe(data, jw);

            assertThat(jw.toString()).isEqualTo(expectedResult);
        }
    }
}
