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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class ExecuteCmdServlet extends HttpServlet {

    private enum Variant {
        WAIT_FOR,
        DESTROY
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        String[] cmd = new String[]{getJavaBinaryPath(), "-version"};

        String variant = req.getParameter("variant");
        Variant v = variant != null ? Variant.valueOf(variant) : Variant.WAIT_FOR;

        int returnValue;

        try {
            Process process = Runtime.getRuntime().exec(cmd);
            switch (v) {
                case DESTROY:
                    process.destroy();
                    returnValue = -1;
                    break;
                case WAIT_FOR:
                    returnValue = process.waitFor();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            PrintWriter writer = resp.getWriter();
            writeMsg(writer, "using variant = %s", v);
            writeMsg(writer, "command = %s", Arrays.toString(cmd));
            writeMsg(writer, "return code = %d", returnValue);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static void writeMsg(PrintWriter writer, String msg, Object... msgArgs) {
        writer.println(String.format(msg, msgArgs));
    }

    private static String getJavaBinaryPath() {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        String executable = isWindows ? "java.exe" : "java";
        Path path = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException("unable to find java path");
        }
        return path.toAbsolutePath().toString();
    }

}
