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

import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.EOF;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.FROM;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.IDENT;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.LPAREN;
import static co.elastic.apm.agent.jdbc.signature.Scanner.Token.RPAREN;

public class SignatureParser {

    private final Scanner scanner = new Scanner();

    public void querySignature(String query, StringBuilder signature) {
        scanner.setQuery(query);
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
