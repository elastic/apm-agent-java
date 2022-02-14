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
package co.elastic.apm.agent.azurestorage;

import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class BlobUrlTest {

    @Test
    void Should_Create_Uri_Azure() throws Exception {
        URL url = new URL("https" , "account.blob.core.windows.net", "/container");
        BlobUrl blobUrl = new BlobUrl(url);
        assertThat(blobUrl.getFullyQualifiedNamespace()).isEqualTo("account.blob.core.windows.net");
        assertThat(blobUrl.getStorageAccountName()).isEqualTo("account");
        assertThat(blobUrl.getResourceName()).isEqualTo("/container");
    }

    @Test
    void Should_Create_Uri_Azurite() throws Exception {
        URL url = new URL("http" , "127.0.0.1", "/account/container");
        BlobUrl blobUrl = new BlobUrl(url);
        assertThat(blobUrl.getFullyQualifiedNamespace()).isEqualTo("127.0.0.1");
        assertThat(blobUrl.getStorageAccountName()).isEqualTo("account");
        assertThat(blobUrl.getResourceName()).isEqualTo("/container");
    }
}
