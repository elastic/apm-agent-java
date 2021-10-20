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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class JakartaJBossMBeanTestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            if (ManagementFactory.getPlatformMBeanServer()
                .queryMBeans(new ObjectName("jboss.as:*"), null)
                .isEmpty()) {
                throw new IllegalStateException("No jboss.as:* MBeans found");
            } else {
                resp.getWriter().write("Found jboss.as:* MBeans");
            }
        } catch (MalformedObjectNameException e) {
            throw new ServletException(e);
        }
    }
}
