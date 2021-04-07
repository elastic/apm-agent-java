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

import co.elastic.apm.api.ElasticApm;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

import static co.elastic.webapp.Constants.CAUSE_DB_ERROR;
import static co.elastic.webapp.Constants.CAUSE_TRANSACTION_ERROR;
import static co.elastic.webapp.Constants.DB_ERROR;
import static co.elastic.webapp.Constants.TRANSACTION_FAILURE;

public class TestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        // just to test public API availability
        if (!ElasticApm.currentTransaction().isSampled()) {
            throw new IllegalStateException("Current transaction is not sampled: " + ElasticApm.currentTransaction());
        }

        boolean causeDbError = req.getParameter(CAUSE_DB_ERROR) != null;
        boolean causeServletError = req.getParameter(CAUSE_TRANSACTION_ERROR) != null;
        String sleep = req.getParameter("sleep");
        if (sleep != null) {
            try {
                Thread.sleep(Long.parseLong(sleep));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // if invoked following AsyncContext.dispatch, original request properties are not available. Instead the query string is made
        // available through this request attribute
        String originalQueryString = (String) req.getAttribute(AsyncContext.ASYNC_QUERY_STRING);
        if (originalQueryString != null) {
            causeDbError |= originalQueryString.contains(CAUSE_DB_ERROR);
            causeServletError |= originalQueryString.contains(CAUSE_TRANSACTION_ERROR);
        }

        Exception cause = null;
        try {
            String content;
            try {
                content = TestDAO.instance().queryDb(causeDbError);
            } catch (SQLException e) {
                cause = e;
                content = DB_ERROR;
            }
            resp.getWriter().append(content);
        } catch (IOException e) {
            cause = e;
        }

        if (causeServletError) {
            throw new ServletException(TRANSACTION_FAILURE, cause);
        }
    }
}
