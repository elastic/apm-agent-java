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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageScannerTest {

    @Test
    void getClassNames() throws Exception {
        assertThat(PackageScanner.getClassNames(getClass().getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(PackageScanner.class.getName());
    }

    @Test
    void testScanJar() throws Exception {
        assertThat(PackageScanner.getClassNames(ByteBuddy.class.getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(ByteBuddy.class.getName());
        // scan again to see verify there's no FileSystemAlreadyExistsException
        assertThat(PackageScanner.getClassNames(ByteBuddy.class.getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(ByteBuddy.class.getName());
    }

    @Test
    void getClassNamesOfNonExistentPackage() throws Exception {
        assertThat(PackageScanner.getClassNames("foo.bar", ElasticApmAgent.getAgentClassLoader())).isEmpty();
    }
}
