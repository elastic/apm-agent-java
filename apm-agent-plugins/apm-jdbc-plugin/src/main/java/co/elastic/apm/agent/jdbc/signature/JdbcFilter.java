/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.jdbc.signature;

class JdbcFilter {

    private boolean inQuote = false;
    private boolean inJdbcEscape = false;
    private boolean jdbcKeyWord = false;

    boolean skip(Scanner s, char c) {
        switch (c) {
            case '{':
                if (!inQuote) {
                    inJdbcEscape = true;
                    jdbcKeyWord = true;
                    return true;
                }
                break;
            case 'o':
            case 'O':
                if (!inQuote && inJdbcEscape && jdbcKeyWord && s.isNextCharIgnoreCase('j')) {
                    s.next();
                    jdbcKeyWord = false;
                    return true;
                }
                break;
            case '}':
                if (!inQuote) {
                    inJdbcEscape = false;
                    return true;
                }
                break;
            case '?':
            case '=':
                if (!inQuote && inJdbcEscape) {
                    return true;
                }
                break;
            case '\'':
                inQuote = !inQuote;
                break;
        }
        jdbcKeyWord = false;
        return false;
    }

    void reset() {
        inQuote = false;
        inJdbcEscape = false;
        jdbcKeyWord = false;
    }
}
