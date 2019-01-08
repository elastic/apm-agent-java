/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.jdbc;

public class JdbcUtils {
    public static final String DB_SPAN_TYPE_PREFIX = "db.";
    public static final String DB_SPAN_TYPE_SUFFIX = ".query";

    public static String computeJdbcSpanTypeName(String dbVendor) {
        return DB_SPAN_TYPE_PREFIX + dbVendor + DB_SPAN_TYPE_SUFFIX;
    }
}
