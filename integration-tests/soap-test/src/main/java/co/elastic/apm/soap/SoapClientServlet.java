/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.soap;

import co.elastic.apm.api.ElasticApm;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.io.IOException;
import java.net.URL;

public class SoapClientServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        QName serviceName = new QName("elastic", "HelloWorldService");
        final HelloWorldService service = Service
            .create(new URL("http", req.getLocalAddr(), req.getLocalPort(),req.getServletContext().getContextPath() + "/HelloWorldService?wsdl"), serviceName)
            .getPort(HelloWorldService.class);
        final String remoteTraceId = service.sayHello();
        if (!ElasticApm.currentTransaction().getTraceId().equals(remoteTraceId)) {
            throw new IllegalStateException(String.format("Trace not propagated local=%s remote=%s",
                ElasticApm.currentTransaction().getTraceId(), remoteTraceId));
        }
        resp.getWriter().print(remoteTraceId);
    }
}
