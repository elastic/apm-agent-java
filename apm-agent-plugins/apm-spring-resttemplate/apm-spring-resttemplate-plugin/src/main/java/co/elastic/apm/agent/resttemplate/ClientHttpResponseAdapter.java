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
package co.elastic.apm.agent.resttemplate;

import org.springframework.http.client.ClientHttpResponse;

public class ClientHttpResponseAdapter {

    private static final int UNKNOWN_STATUS = -1;

    public static int getStatusCode(ClientHttpResponse response) {
        int status = internalGetStatusCode(response);
        if (status == UNKNOWN_STATUS) {
            status = legacyGetStatusCode(response);
        }
        return status;
    }

    private static int internalGetStatusCode(ClientHttpResponse response) {
        try {
            return response.getStatusCode().value();
        } catch (Exception|Error e) {
            // using broad exception to handle when method is missing for pre 6.x versions
            return UNKNOWN_STATUS;
        }
    }

    private static int legacyGetStatusCode(ClientHttpResponse response) {
        // getRawStatusCode has been introduced in 3.1.1
        // but deprecated in 6.x, will be removed in 7.x (using method handle will be needed).
        try {
            return response.getRawStatusCode();
        } catch (Exception|Error e) {
            // using broad exception to handle when method is missing in pre-3.1.1 and post 7.x
            return UNKNOWN_STATUS;
        }
    }

    private ClientHttpResponseAdapter() {

    }
}
