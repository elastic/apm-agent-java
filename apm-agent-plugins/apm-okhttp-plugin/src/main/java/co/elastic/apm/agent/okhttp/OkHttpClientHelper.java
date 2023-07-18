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
package co.elastic.apm.agent.okhttp;

import javax.annotation.Nullable;


public class OkHttpClientHelper {

    /**
     * If this method needs to perform corrections on the hostname, it has to allocate a new StringBuilder.
     * We accept this due to the fact that this method is called once per HTTP call, making the allocation neglectable
     * overhead compared to the allocations performed for the HTTP call itself.
     *
     * @param originalHostName the original host name retrieved from the OkHttp client
     * @return the potentially corrected host name
     */
    @Nullable
    public static CharSequence computeHostName(@Nullable String originalHostName) {
        CharSequence hostName = originalHostName;
        // okhttp represents IPv6 addresses without square brackets, as opposed to all others, so we should add them
        if (originalHostName != null && originalHostName.contains(":") && !originalHostName.startsWith("[")) {
            StringBuilder sb = new StringBuilder(originalHostName.length() + 2);
            sb.setLength(0);
            sb.append("[").append(originalHostName).append("]");
            hostName = sb;
        }
        return hostName;
    }
}
