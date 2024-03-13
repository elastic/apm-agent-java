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
package co.elastic.apm.agent.sdk.internal.db.signature;

import co.elastic.apm.agent.sdk.internal.collections.LRUCache;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectHandle;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPool;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPooling;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.Callable;

public class SignatureParser {

    private final ObjectPool<? extends ObjectHandle<Scanner>> scannerPool;

    private final Map<String, String[]> signatureCache = LRUCache.createCache(1000);

    public SignatureParser() {
        this(new Callable<Scanner>() {
            @Override
            public Scanner call() {
                return new Scanner();
            }
        });
    }

    public SignatureParser(final Callable<Scanner> scannerAllocator) {
        scannerPool = ObjectPooling.createWithDefaultFactory(scannerAllocator);
    }

    public void querySignature(String query, StringBuilder signature, boolean preparedStatement) {
        querySignature(query, signature, null, preparedStatement);
    }

    public void querySignature(String query, StringBuilder signature, @Nullable StringBuilder dbLink, boolean preparedStatement) {

        final String[] cachedSignature = signatureCache.get(query);
        if (cachedSignature != null) {
            signature.append(cachedSignature[0]);
            if (dbLink != null) {
                dbLink.append(cachedSignature[1]);
            }
            return;
        }
        try (ObjectHandle<Scanner> pooledScanner = scannerPool.createInstance()) {
            Scanner scanner = pooledScanner.get();
            scanner.setQuery(query);
            parse(scanner, query, signature, dbLink);

            signatureCache.put(query, new String[]{signature.toString(), dbLink != null ? dbLink.toString() : ""});
        }
    }

    private void parse(Scanner scanner, String query, StringBuilder signature, @Nullable StringBuilder dbLink) {
        final Scanner.Token firstToken = scanner.scanWhile(Scanner.Token.COMMENT);
        switch (firstToken) {
            case CALL:
                signature.append("CALL");
                if (scanner.scanUntil(Scanner.Token.IDENT)) {
                    appendIdentifiers(scanner, signature, dbLink);
                }
                return;
            case DELETE:
                signature.append("DELETE");
                if (scanner.scanUntil(Scanner.Token.FROM) && scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(" FROM");
                    appendIdentifiers(scanner, signature, dbLink);
                }
                return;
            case INSERT:
            case REPLACE:
                signature.append(firstToken.name());
                if (scanner.scanUntil(Scanner.Token.INTO) && scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(" INTO");
                    appendIdentifiers(scanner, signature, dbLink);
                }
                return;
            case SELECT:
                signature.append("SELECT");
                int level = 0;
                for (Scanner.Token t = scanner.scan(); t != Scanner.Token.EOF; t = scanner.scan()) {
                    if (t == Scanner.Token.LPAREN) {
                        level++;
                    } else if (t == Scanner.Token.RPAREN) {
                        level--;
                    } else if (t == Scanner.Token.FROM) {
                        if (level == 0) {
                            if (scanner.scanToken(Scanner.Token.IDENT)) {
                                signature.append(" FROM");
                                appendIdentifiers(scanner, signature, dbLink);
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
                boolean hasPeriod = false, hasFirstPeriod = false, isDbLink = false;
                if (scanner.scanToken(Scanner.Token.IDENT)) {
                    signature.append(' ');
                    scanner.appendCurrentTokenText(signature);
                    for (Scanner.Token t = scanner.scan(); t != Scanner.Token.EOF; t = scanner.scan()) {
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
                                } else if (isDbLink) {
                                    if (dbLink != null) {
                                        scanner.appendCurrentTokenText(dbLink);
                                    }
                                    isDbLink = false;
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
                                if ("@".equals(scanner.text())) {
                                    isDbLink = true;
                                    break;
                                } else {
                                    return;
                                }
                        }
                    }
                }
                return;
            case MERGE:
                signature.append("MERGE");
                if (scanner.scanToken(Scanner.Token.INTO) && scanner.scanUntil(Scanner.Token.IDENT)) {
                    signature.append(" INTO");
                    appendIdentifiers(scanner, signature, dbLink);
                }
                return;
            default:
                query = query.trim();
                final int indexOfWhitespace = query.indexOf(' ');
                signature.append(query, 0, indexOfWhitespace > 0 ? indexOfWhitespace : query.length());
        }
    }

    private void appendIdentifiers(Scanner scanner, StringBuilder signature, @Nullable StringBuilder dbLink) {
        signature.append(' ');
        scanner.appendCurrentTokenText(signature);
        boolean connectedIdents = false, isDbLink = false;
        for (Scanner.Token t = scanner.scan(); t != Scanner.Token.EOF; t = scanner.scan()) {
            switch (t) {
                case IDENT:
                    // do not add tokens which are separated by a space
                    if (connectedIdents) {
                        scanner.appendCurrentTokenText(signature);
                        connectedIdents = false;
                    } else {
                        if (isDbLink) {
                            if (dbLink != null) {
                                scanner.appendCurrentTokenText(dbLink);
                            }
                        }
                        return;
                    }
                    break;
                case PERIOD:
                    signature.append('.');
                    connectedIdents = true;
                    break;
                case USING:
                    return;
                default:
                    if ("@".equals(scanner.text())) {
                        isDbLink = true;
                    }
                    break;
            }
        }
    }
}
