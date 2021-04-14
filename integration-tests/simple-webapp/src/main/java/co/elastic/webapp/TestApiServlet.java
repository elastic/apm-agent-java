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
package co.elastic.webapp;

import co.elastic.apm.api.CaptureSpan;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Span;
import co.elastic.apm.api.Transaction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TestApiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {
        Transaction transaction = ElasticApm.currentTransaction();

        // set transaction name
        transaction.setName("custom_transaction_name");

        // set transaction type, here default value is 'request'
        transaction.setType("custom_transaction_type");

        // set custom transaction labels
        transaction.addLabel("custom-label1", "label_value1");
        transaction.addLabel("custom-label2", "label_value2");

        // store custom context field
        transaction.addCustomContext("custom-context", "custom-context-value");

        // creating a custom span with annotation
        captureSpanAnnotation();

        doWork();

        createCustomSpan();

    }

    private void createCustomSpan() {
        Span span = ElasticApm.currentTransaction().startSpan("db", "mysql", "query");
        try {
            span.setName("SELECT FROM customer");
            doWork();
        } catch (Exception e) {
            span.captureException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    // requires configuration application_packages to be set, otherwise it's ignored
    @CaptureSpan
    private void captureSpanAnnotation() {
        doWork();

        // we can still access the current span
        ElasticApm.currentSpan().addLabel("span-label", "label-value");
    }

    private static void doWork() {
        // let's pretend we do some actual work here
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // silently ignored
        }
    }

}
