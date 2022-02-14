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

import java.net.URL;

public class BlobUrl {
    private String resourceName;
    private String storageAccountName;
    private String fullyQualifiedNamespace;
    private int port;

    public BlobUrl(URL url) {
        resourceName = url.getPath();
        port = url.getPort();
        if (port <= 0) {
            port = 80;
        }
        String[] split = url.getHost().split("\\.");
        if (split.length > 0) {
            storageAccountName = split[0];
        }
        fullyQualifiedNamespace = url.getHost();
        if ("127".equals(storageAccountName)) {
            // Dev Mode (azurite) account name is not on url
            // https://docs.microsoft.com/en-us/azure/storage/common/storage-configure-connection-string
            String[] split1 = resourceName.split("/");
            if (split1.length > 1) {
                storageAccountName = split1[1];
                resourceName = resourceName.substring(storageAccountName.length() + 1);
            }
        }
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getStorageAccountName() {
        return storageAccountName;
    }

    public String getFullyQualifiedNamespace() {
        return fullyQualifiedNamespace;
    }

    public int getPort() {
        return port;
    }
}
