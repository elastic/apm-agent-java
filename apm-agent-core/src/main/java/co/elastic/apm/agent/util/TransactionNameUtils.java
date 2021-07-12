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

import javax.annotation.Nullable;

public class TransactionNameUtils {

    public static void setTransactionNameByServletClass(@Nullable String method, @Nullable Class<?> servletClass, @Nullable StringBuilder transactionName) {
        if (servletClass == null) {
            return;
        }
        if (transactionName == null) {
            return;
        }
        String servletClassName = servletClass.getName();
        transactionName.append(servletClassName, servletClassName.lastIndexOf('.') + 1, servletClassName.length());
        if (method != null) {
            transactionName.append('#');
            switch (method) {
                case "DELETE":
                    transactionName.append("doDelete");
                    break;
                case "HEAD":
                    transactionName.append("doHead");
                    break;
                case "GET":
                    transactionName.append("doGet");
                    break;
                case "OPTIONS":
                    transactionName.append("doOptions");
                    break;
                case "POST":
                    transactionName.append("doPost");
                    break;
                case "PUT":
                    transactionName.append("doPut");
                    break;
                case "TRACE":
                    transactionName.append("doTrace");
                    break;
                default:
                    transactionName.append(method);
            }
        }
    }
}
