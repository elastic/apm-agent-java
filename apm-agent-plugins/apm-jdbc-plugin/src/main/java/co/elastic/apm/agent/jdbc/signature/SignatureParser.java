/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.EOF;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.FROM;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.IDENT;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.LPAREN;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.RPAREN;

public class SignatureParser {

    /**
     * If the cache reaches this size we assume that the application creates a lot of dynamic queries.
     * In that case it's inefficient to try to cache these as they are not likely to be repeated.
     * But we still pay the price of allocating a Map.Entry and a String for the signature.
     */
    private static final int DISABLE_CACHE_THRESHOLD = 512;
    /**
     * The cache management overhead is probably not worth it for short queries
     */
    private static final int QUERY_LENGTH_CACHE_LOWER_THRESHOLD = 64;
    /**
     * We don't want to keep alive references to huge query strings
     */
    private static final int QUERY_LENGTH_CACHE_UPPER_THRESHOLD = 10_000;
    private final Scanner scanner = new Scanner();
    /**
     * Not using weak keys as the DISABLE_CACHE_THRESHOLD may not be reached when clearing stale entries.
     * That means that we are leaking Strings but as the size of the map is limited that should not be an issue.
     * Another reason is that ORMs like Hibernate generate equal SQL strings for the same query but don't reuse the same string instance.
     * When relying on weak keys, we would not leverage any caching benefits if the query string is collected.
     */
    @Nullable
    private ConcurrentMap<String, String> signatureCache = new ConcurrentHashMap<>(DISABLE_CACHE_THRESHOLD);

    public void querySignature(String query, StringBuilder signature, boolean preparedStatement) {
        ConcurrentMap<String, String> localCache = null;
        // non-prepared statements are likely to be dynamic strings
        if (preparedStatement && QUERY_LENGTH_CACHE_LOWER_THRESHOLD < query.length() && query.length() < QUERY_LENGTH_CACHE_UPPER_THRESHOLD) {
            // no volatile read needed
            // the signatureCache = null at the end of this method makes this eventually consistent on all threads
            localCache = signatureCache;
            if (localCache != null) {
                final String cachedSignature = localCache.get(query);
                if (cachedSignature != null) {
                    signature.append(cachedSignature);
                    return;
                }
            }
        }

        scanner.setQuery(query);
        parse(query, signature);

        if (localCache != null) {
            if (localCache.size() <= DISABLE_CACHE_THRESHOLD) {
                // we don't mind a small overshoot due to race conditions
                localCache.put(query, signature.toString());
            } else {
                signatureCache = null;
            }
        }
    }

    private void parse(String query, StringBuilder signature) {
        final Scanner.Token firstToken = scanner.scanWhile(Scanner.Token.COMMENT);
        switch (firstToken) {
            case CALL:
                signature.append("CALL");
                if (scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(' ');
                    scanner.appendCurrentTokenText(signature);
                }
                return;
            case DELETE:
                signature.append("DELETE");
                if (scanner.scanUntil(FROM) && scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(" FROM ");
                    appendIdentifiers(signature);
                }
                return;
            case INSERT:
            case REPLACE:
                signature.append(firstToken.name());
                if (scanner.scanUntil(Scanner.Token.INTO) && scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(" INTO ");
                    appendIdentifiers(signature);
                }
                return;
            case SELECT:
                signature.append("SELECT");
                int level = 0;
                for (Scanner.Token t = scanner.scan(); t != EOF; t = scanner.scan()) {
                    if (t == LPAREN) {
                        level++;
                    } else if (t == RPAREN) {
                        level--;
                    } else if (t == FROM) {
                        if (level == 0) {
                            if (scanner.scanToken(Scanner.Token.IDENT)) {
                                signature.append(" FROM ");
                                appendIdentifiers(signature);
                            } else {
                                return;
                            }
                        }
                    }
                }
                return;
            case UPDATE:
                signature.append("UPDATE");
                // Scan for the table name
                boolean hasPeriod = false, hasFirstPeriod = false;
                if (scanner.scanToken(IDENT)) {
                    signature.append(' ');
                    scanner.appendCurrentTokenText(signature);
                    for (Scanner.Token t = scanner.scan(); t != EOF; t = scanner.scan()) {
                        switch (t) {
                            case IDENT:
                                if (hasPeriod) {
                                    scanner.appendCurrentTokenText(signature);
                                    hasPeriod = false;
                                }
                                if (!hasFirstPeriod) {
                                    // Some dialects allow option keywords before the table name
                                    // example: UPDATE IGNORE foo.bar
                                    signature.setLength(0);
                                    signature.append("UPDATE ");
                                    scanner.appendCurrentTokenText(signature);
                                }
                                // Two adjacent identifiers found after the first period.
                                // Ignore the secondary ones, in case they are unknown keywords.
                                break;
                            case PERIOD:
                                hasFirstPeriod = true;
                                hasPeriod = true;
                                signature.append('.');
                                break;
                            default:
                                return;
                        }
                    }
                }
                return;
            default:
                query = query.trim();
                final int indexOfWhitespace = query.indexOf(' ');
                signature.append(query, 0, indexOfWhitespace > 0 ? indexOfWhitespace : query.length());
        }
    }

    private void appendIdentifiers(StringBuilder signature) {
        scanner.appendCurrentTokenText(signature);
        while (scanner.scanToken(Scanner.Token.PERIOD) && scanner.scanToken(Scanner.Token.IDENT)) {
            signature.append('.');
            scanner.appendCurrentTokenText(signature);
        }
    }
}
