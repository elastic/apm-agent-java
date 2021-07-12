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

import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;

import javax.annotation.Nullable;

public class OkHttpClientHelper {

    /**
     * Used to avoid allocations when calculating destination host name.
     */
    public static final GlobalThreadLocal<StringBuilder> destinationHostName = GlobalThreadLocal.get(OkHttpClientHelper.class, "destinationHostName", new StringBuilder());

    /**
     * NOTE: this method returns a StringBuilder instance that is kept as this class's ThreadLocal. Callers of this
     * method MAY NOT KEEP A REFERENCE TO THE RETURNED OBJECT, only copy its contents.
     *
     * @param originalHostName the original host name retrieved from the OkHttp client
     * @return a StringBuilder instance that is kept as a ThreadLocal
     */
    @Nullable
    public static CharSequence computeHostName(@Nullable String originalHostName) {
        CharSequence hostName = originalHostName;
        // okhttp represents IPv6 addresses without square brackets, as opposed to all others, so we should add them
        if (originalHostName != null && originalHostName.contains(":") && !originalHostName.startsWith("[")) {
            StringBuilder sb = destinationHostName.get();
            sb.setLength(0);
            sb.append("[").append(originalHostName).append("]");
            hostName = sb;
        }
        return hostName;
    }
}
