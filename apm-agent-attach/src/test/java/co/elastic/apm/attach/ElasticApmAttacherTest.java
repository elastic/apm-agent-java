/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.attach;

import org.junit.jupiter.api.Test;
import wiremock.org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmAttacherTest {

    @Test
    void testHash() throws Exception {
        assertThat(ElasticApmAttacher.md5Hash(getClass().getResourceAsStream(ElasticApmAttacher.class.getSimpleName() + ".class")))
            .isEqualTo(DigestUtils.md5Hex(getClass().getResourceAsStream(ElasticApmAttacher.class.getSimpleName() + ".class")));
    }

    @Test
    void testCreateTempProperties() throws Exception {
        File tempProperties = ElasticApmAttacher.createTempProperties(Map.of("foo", "bär"));
        assertThat(tempProperties).isNotNull();
        tempProperties.deleteOnExit();
        Properties properties = new Properties();
        properties.load(new FileReader(tempProperties));
        assertThat(properties.get("foo")).isEqualTo("bär");

    }
}
