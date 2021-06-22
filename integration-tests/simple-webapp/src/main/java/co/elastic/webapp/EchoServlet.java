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

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EchoServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        final ServletOutputStream os = resp.getOutputStream();
        final ServletInputStream is = req.getInputStream();
        switch (req.getParameter("read-method")) {
            case "read-byte":
                while ((read = is.read()) > 0) {
                    os.write(read);
                }
                break;
            case "read-bytes":
                while ((read = is.read(buffer)) > 0) {
                    os.write(buffer, 0, read);
                }
                break;
            case "read-offset":
                while ((read = is.read(buffer, 42, buffer.length / 2)) > 0) {
                    os.write(buffer, 42, read);
                }
                break;
            case "read-line":
                while ((read = is.readLine(buffer, 0, buffer.length)) > 0) {
                    os.write(buffer, 0, read);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid read-method");
        }
        resp.setContentType(req.getContentType());
    }
}
