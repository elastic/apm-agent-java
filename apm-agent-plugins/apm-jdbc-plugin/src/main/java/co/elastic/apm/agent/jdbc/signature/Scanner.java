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

public class Scanner {

    private String input = "";
    private int start; // text start char offset
    private int end; // text end char offset
    private int pos; // read position char offset
    private int inputLength;
    private final JdbcFilter filter = new JdbcFilter();

    public void setQuery(String sql) {
        this.input = sql;
        filter.reset();
        inputLength = sql.length();
        start = 0;
        end = 0;
        pos = 0;
    }

    public Token scanWhile(Token token) {
        for (Token t = scan(); t != Token.EOF; t = scan()) {
            if (t != token) {
                return t;
            }
        }
        return Token.EOF;
    }

    public boolean scanUntil(Token token) {
        for (Token t = scan(); t != Token.EOF; t = scan()) {
            if (t == token) {
                return true;
            }
        }
        return false;
    }

    public boolean scanToken(Token token) {
        for (Token t = scan(); t != Token.EOF; t = scan()) {
            if (t == token) {
                return true;
            } else if (t != Token.COMMENT) {
                return false;
            }
        }
        return false;
    }

    public Token scan() {
        if (!hasNext()) {
            return Token.EOF;
        }
        char c = next();
        while (Character.isSpaceChar(c) || filter.skip(this, c)) {
            if (hasNext()) {
                c = next();
            } else {
                return Token.EOF;
            }
        }
        start = pos - 1;
        if (c == '_' || Character.isLetter(c)) {
            return scanKeywordOrIdentifier(c != '_');
        } else if (Character.isDigit(c)) {
            return scanNumericLiteral();
        }
        switch (c) {
            case '\'':
                // Standard string literal
                return scanStringLiteral();
            case '"':
                // Standard double-quoted identifier.
                //
                // NOTE(axw) MySQL will treat " as a
                // string literal delimiter by default,
                // but we assume standard SQL and treat
                // it as a identifier delimiter.
                return scanQuotedIdentifier('"');
            case '[':
                // T-SQL bracket-quoted identifier
                return scanQuotedIdentifier(']');
            case '`':
                // MySQL-style backtick-quoted identifier
                return scanQuotedIdentifier('`');
            case '(':
                return Token.LPAREN;
            case ')':
                return Token.RPAREN;
            case '-':
                if (isNextChar('-')) {
                    // -- comment
                    next();
                    return scanSimpleComment();
                }
                return Token.OTHER;
            case '/':
                if (isNextChar('*')) {
                    // /* comment */
                    next();
                    return scanBracketedComment();
                }
                return Token.OTHER;
            case '.':
                return Token.PERIOD;
            case '$':
                if (!hasNext()) {
                    return Token.OTHER;
                }
                char next = peek();
                if (Character.isDigit(next)) {
                    while (hasNext()) {
                        if (!Character.isDigit(peek())) {
                            break;
                        } else {
                            next();
                        }
                    }
                    return Token.OTHER;
                } else if (next == '$' || next == '_' || Character.isLetter(next)) {
                    // PostgreSQL supports dollar-quoted string literal syntax, like $foo$...$foo$.
                    // The tag (foo in this case) is optional, and if present follows identifier rules.
                    while (hasNext()) {
                        c = next();
                        if (c == '$') {
                            // This marks the end of the initial $foo$.
                            final String text = text();
                            int i = input.indexOf(text, pos);
                            if (i >= 0) {
                                end = i + text.length();
                                pos = i + text.length();
                                return Token.STRING;
                            }
                            return Token.OTHER;
                        } else if (Character.isLetter(c) || Character.isDigit(c) || c == '_') {
                            // Identifier char, consume
                        } else if (Character.isSpaceChar(c)) {
                            end--;
                            return Token.OTHER;
                        }
                    }
                    // Unknown token starting with $ until EOF, just ignore it.
                    return Token.OTHER;
                }
            default:
                return Token.OTHER;
        }
    }

    private Token scanKeywordOrIdentifier(boolean maybeKeyword) {
        while (hasNext()) {
            char c = peek();
            if (Character.isDigit(c) || c == '_' || c == '$') {
                maybeKeyword = false;
            } else if (!Character.isLetter(c)) {
                break;
            }
            next();
        }
        if (!maybeKeyword) {
            return Token.IDENT;
        }
        for (Token token : Token.getKeywordsByLength(textLength())) {
            if (isTextEqualIgnoreCase(token.name())) {
                return token;
            }
        }
        return Token.IDENT;
    }

    private Token scanNumericLiteral() {
        boolean hasPeriod = false;
        boolean hasExponent = false;
        while (hasNext()) {
            char c = peek();
            if (Character.isDigit(c)) {
                next();
                continue;
            }
            switch (c) {
                case '.':
                    if (hasPeriod) {
                        return Token.NUMBER;
                    }
                    next();
                    hasPeriod = true;
                    break;
                case 'e':
                case 'E':
                    if (hasExponent) {
                        return Token.NUMBER;
                    }
                    next();
                    hasExponent = true;
                    if (isNextChar('+') || isNextChar('-')) {
                        next();
                    }
                    break;
                default:
                    return Token.NUMBER;
            }
        }
        return Token.NUMBER;
    }

    private Token scanStringLiteral() {
        while (hasNext()) {
            char c = next();
            if (c == '\\' && hasNext()) {
                // skip escaped character
                // example: 'what\'s up?'
                next();
            } else if (c == '\'') {
                if (isNextChar('\'')) {
                    // skip escaped single quote
                    // example: 'what''s up?'
                    next();
                } else {
                    // end of string
                    return Token.STRING;
                }
            }
        }
        return Token.EOF;
    }

    private Token scanQuotedIdentifier(char delimiter) {
        while (hasNext()) {
            char c = next();
            if (c == delimiter) {
                if (delimiter == '"' && isNextChar('"')) {
                    // skip escaped double quote
                    // example: "He said ""great"""
                    next();
                    continue;
                }
                // remove quotes from identifier
                start++;
                end--;
                return Token.IDENT;
            }
        }
        return Token.EOF;
    }

    private Token scanSimpleComment() {
        while (hasNext()) {
            if (next() == '\n') {
                return Token.COMMENT;
            }
        }
        return Token.COMMENT;
    }

    private Token scanBracketedComment() {
        int nesting = 1;
        while (hasNext()) {
            char c = next();
            switch (c) {
                case '/':
                    if (isNextChar('*')) {
                        next();
                        nesting++;
                    }
                case '*':
                    if (isNextChar('/')) {
                        next();
                        nesting--;
                        if (nesting == 0) {
                            return Token.COMMENT;
                        }
                    }
            }
        }
        return Token.EOF;
    }

    private char peek() {
        return input.charAt(pos);
    }

    char next() {
        final char c = peek();
        pos++;
        end = pos;
        return c;
    }

    private boolean hasNext() {
        return pos < inputLength;
    }

    private boolean isTextEqualIgnoreCase(String name) {
        return input.regionMatches(true, start, name, 0, textLength());
    }

    /**
     * Returns the portion of the SQL that relates to the most recently scanned token.
     * <p>
     * Note: this method allocates memory and thus should only be used in tests.
     * </p>
     *
     * @return the portion of the SQL that relates to the most recently scanned token
     */
    String text() {
        final StringBuilder sb = new StringBuilder();
        appendCurrentTokenText(sb);
        return sb.toString();
    }

    /**
     * Appends the portion of the SQL that relates to the most recently scanned token to the provided {@link StringBuilder}.
     *
     * @param sb the {@link StringBuilder} which will be used to append the SQL
     */
    public void appendCurrentTokenText(StringBuilder sb) {
        sb.append(input, start, end);
    }

    public int textLength() {
        return end - start;
    }

    private boolean isNextChar(char c) {
        return hasNext() && peek() == c;
    }

    boolean isNextCharIgnoreCase(char c) {
        return hasNext() && Character.toLowerCase(peek()) == Character.toLowerCase(c);
    }

    public enum Token {

        OTHER,
        EOF,
        COMMENT,

        IDENT, // includes unhandled keywords
        NUMBER, // 123, 123.45, 123e+45
        STRING, // 'foo'

        PERIOD, // .
        LPAREN, // (
        RPAREN, // )

        AS,
        CALL,
        DELETE,
        FROM,
        INSERT,
        INTO,
        OR,
        REPLACE,
        SELECT,
        SET,
        TABLE,
        TRUNCATE, // Cassandra/CQL-specific
        UPDATE,
        MERGE,
        USING;

        private static final Token[] EMPTY = {};
        private static final Token[][] KEYWORDS_BY_LENGTH = {
            {},
            {},
            {AS, OR},
            {SET},
            {CALL, FROM, INTO},
            {TABLE, MERGE, USING},
            {DELETE, INSERT, SELECT, UPDATE},
            {REPLACE},
            {TRUNCATE}
        };

        public static Token[] getKeywordsByLength(int length) {
            if (length < KEYWORDS_BY_LENGTH.length) {
                return KEYWORDS_BY_LENGTH[length];
            }
            return EMPTY;
        }

    }
}
