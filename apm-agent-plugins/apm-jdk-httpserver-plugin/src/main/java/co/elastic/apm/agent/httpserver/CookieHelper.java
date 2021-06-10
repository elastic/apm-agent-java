/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2021 Elastic and contributors
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
package co.elastic.apm.agent.httpserver;

import java.util.ArrayList;
import java.util.List;

public class CookieHelper {

    public static List<String[]> getCookies(List<String> headerValues) {
        List<String[]> cookies = new ArrayList<>();
        for (String headerValue : headerValues) {
            for (String s : headerValue.split("[;,]")) {
                String[] nameAndValue = s.split("=", 2);
                String cookieName = nameAndValue.length > 0 ? nameAndValue[0].trim() : "";
                if (!cookieName.startsWith("$")) {
                    String cookieValue = nameAndValue.length > 1 ? nameAndValue[1].trim() : "";
                    if (cookieValue.startsWith("\"") && cookieValue.endsWith("\"") && cookieValue.length() > 1) {
                        cookieValue = cookieValue.substring(1, cookieValue.length() - 1);
                    }

                    cookies.add(new String[]{cookieName, cookieValue});
                }
            }
        }

        return cookies;
    }
}
