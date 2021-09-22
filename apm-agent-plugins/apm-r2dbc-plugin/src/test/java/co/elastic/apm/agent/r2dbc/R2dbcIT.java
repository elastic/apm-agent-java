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
package co.elastic.apm.agent.r2dbc;

import io.r2dbc.spi.ConnectionFactories;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Mono;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class R2dbcIT extends AbstractR2dbcInstrumentationTest {

    public R2dbcIT(String url, String expectedDbVendor) {
        super(Mono.from(ConnectionFactories.get(url).create()).block(), expectedDbVendor);
    }

    @Parameterized.Parameters(name = "{1} {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"r2dbc:tc:mariadb://hostname/databasename?TC_IMAGE_TAG=10", "mariadb"},
//            {"r2dbc:tc:postgresql://hostname/databasename?TC_IMAGE_TAG=9", "postgresql"},
//            {"r2dbc:tc:postgresql://hostname/databasename?TC_IMAGE_TAG=10", "postgresql"},
//            {"r2dbc:tc:mysql://hostname/databasename?TC_IMAGE_TAG=5.7.34&sslMode=disabled", "mysql"},
//            {"r2dbc:tc:sqlserver:///?TC_IMAGE_TAG=2017-CU12", "sqlserver"},
        });
    }
}
