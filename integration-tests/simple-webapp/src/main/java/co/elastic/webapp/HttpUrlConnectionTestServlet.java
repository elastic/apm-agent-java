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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUrlConnectionTestServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        final URL url = new URL("http",  req.getLocalAddr(), req.getLocalPort(),req.getServletContext().getContextPath() + "/hello-world.jsp");
        final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.connect();
        final InputStream inputStream = urlConnection.getInputStream();
        resp.setStatus(urlConnection.getResponseCode());
        resp.setContentType(urlConnection.getHeaderField("Content-Type"));
        final byte[] buffer = new byte[1024];
        final ServletOutputStream outputStream = resp.getOutputStream();
        for (int limit = inputStream.read(buffer); limit != -1; limit = inputStream.read(buffer)) {
            outputStream.write(buffer, 0, limit);
        }
        inputStream.close();
        urlConnection.disconnect();
        outputStream.close();
    }
}
