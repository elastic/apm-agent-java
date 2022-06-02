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
package co.elastic.webapp;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorServiceTestServlet extends HttpServlet {

    private ExecutorService executor;

    @Override
    public void init() {
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        final Transaction transaction = ElasticApm.currentTransaction();
        if (!transaction.isSampled()) {
            throw new IllegalStateException("Transaction is not sampled");
        }

        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    if (!ElasticApm.currentSpan().getId().equals(transaction.getId())) {
                        throw new IllegalStateException("Context not propagated");
                    }
                    ElasticApm.currentSpan().startSpan().setName("Async").end();
                }
            }).get();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

}
