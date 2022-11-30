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
package co.elastic.apm.agent.log4j1.shipper;

import co.elastic.apm.agent.report.Reporter;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

public class LogShipperAppender extends AppenderSkeleton {
    private final Reporter reporter;
    private final Layout formatter;

    public LogShipperAppender(Reporter reporter, Layout formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }

    @Override
    public synchronized void doAppend(LoggingEvent event) {
        append(event);
    }

    @Override
    protected void append(LoggingEvent event) {
        reporter.reportLog(formatter.format(event));
    }

    @Override
    public void close() {

    }

    @Override
    public boolean requiresLayout() {
        return false;
    }
}
