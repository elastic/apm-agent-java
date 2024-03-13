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
package co.elastic.apm.agent.testutils.assertions;

import co.elastic.apm.agent.impl.context.DbImpl;

import javax.annotation.Nullable;
import java.nio.CharBuffer;

public class DbAssert extends BaseAssert<DbAssert, DbImpl> {

    protected DbAssert(DbImpl actual) {
        super(actual, DbAssert.class);
    }

    /**
     * Asserts the statement value
     *
     * @param statement expected statement
     * @return this
     */
    public DbAssert hasStatement(String statement) {
        checkString("DB statement '%s' but was '%s'", statement, getStatementAsString());
        return this;
    }

    /**
     * Asserts that a statement is present (without checking the exact value)
     *
     * @return this
     */
    public DbAssert hasStatement() {
        checkTrue("missing DB statement or statement buffer", getStatementAsString() != null);
        return this;
    }

    @Nullable
    private String getStatementAsString() {
        String value = actual.getStatement();
        CharBuffer statementBuffer = actual.getStatementBuffer();
        if(value != null){
            checkTrue("unexpected non-empty DB statement buffer", statementBuffer == null || statementBuffer.length() > 0);
        } else if (statementBuffer != null) {
            value = statementBuffer.toString();
        }

        return value;
    }

    /**
     * Asserts the instance
     *
     * @param instance expected instance
     * @return this
     */
    public DbAssert hasInstance(String instance) {
        checkString("DB instance '%s' but was '%s'", instance, actual.getInstance());
        return this;
    }

}
