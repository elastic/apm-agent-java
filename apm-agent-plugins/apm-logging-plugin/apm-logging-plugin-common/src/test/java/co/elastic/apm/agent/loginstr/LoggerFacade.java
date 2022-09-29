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
package co.elastic.apm.agent.loginstr;

public interface LoggerFacade {

    void open();

    void close();

    /**
     * @return original log file path
     */
    String getLogFilePath();

    /**
     * @return console log file path, which is used when console log is shaded into a file
     */
    String getConsoleLogFilePath();

    void trace(String message);

    void debug(String message);

    default void debugWithMarker(String message) {
        // do nothing unless supported by framework
    }

    void warn(String message);

    void error(String message);

    void error(String message, Throwable throwable);

}
