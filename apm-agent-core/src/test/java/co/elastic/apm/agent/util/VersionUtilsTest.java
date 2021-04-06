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
package co.elastic.apm.agent.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class VersionUtilsTest {

    @Test
    void testGetVersionFromPackage() {
        assertThat(VersionUtils.getVersionFromPackage(Test.class)).isNotEmpty();
        assertThat(VersionUtils.getVersionFromPackage(Test.class))
            .isEqualTo(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"));
        assertThat(VersionUtils.getManifestEntry(new File(Test.class.getProtectionDomain().getCodeSource().getLocation().getFile()), "Implementation-Version"))
            .isEqualTo(VersionUtils.getVersionFromPackage(Test.class));

        // tests caching
        assertThat(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"))
            .isSameAs(VersionUtils.getVersion(Test.class, "org.junit.jupiter", "junit-jupiter-api"));
    }

    @Test
    void getVersionFromPomProperties() {
        assertThat(VersionUtils.getVersionFromPomProperties(Assertions.class, "org.assertj", "assertj-core")).isNotEmpty();
        assertThat(VersionUtils.getVersionFromPomProperties(Assertions.class, "org.assertj", "assertj-core"))
            .isEqualTo(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"));
        // tests caching
        assertThat(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"))
            .isSameAs(VersionUtils.getVersion(Assertions.class, "org.assertj", "assertj-core"));
    }
}
