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
package co.elastic.apm.agent.testutils;

import javax.net.ServerSocketFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;

public class TestPort {

    private static final Random rand = new Random(System.currentTimeMillis());

    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65536;

    /**
     * @return random available TCP port
     */
    public static synchronized int getAvailableRandomPort() {
        int port;
        do {
            port = MIN_PORT + rand.nextInt(MAX_PORT - MIN_PORT + 1);
        } while (!isAvailablePort(port));
        return port;
    }

    private static boolean isAvailablePort(int port) {
        try {
            ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(port, 1, InetAddress.getByName("localhost"));
            serverSocket.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
