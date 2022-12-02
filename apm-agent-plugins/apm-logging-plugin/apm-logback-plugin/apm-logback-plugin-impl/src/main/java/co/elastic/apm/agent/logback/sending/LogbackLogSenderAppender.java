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
package co.elastic.apm.agent.logback.sending;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import co.elastic.apm.agent.report.Reporter;

public class LogbackLogSenderAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final Reporter reporter;
    private final Encoder<ILoggingEvent> formatter;

    public LogbackLogSenderAppender(Reporter reporter, Encoder<ILoggingEvent> formatter) {
        this.reporter = reporter;
        this.formatter = formatter;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        reporter.reportLog(formatter.encode(eventObject));
    }
}
