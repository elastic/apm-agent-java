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
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.logging.logback.EcsEncoder;

public class LogbackLogSenderAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    private final Tracer tracer;
    private final EcsEncoder formatter;

    public LogbackLogSenderAppender(Tracer tracer, Encoder<ILoggingEvent> formatter) {
        this.tracer = tracer;
        // Due to API compatibility (see below in 'append'), we have to use our own formatter type rather than the
        // base/interface class from logback.
        if (!(formatter instanceof EcsEncoder)) {
            throw new IllegalArgumentException("ECS sender requires to use an ECS appender");
        }
        this.formatter = (EcsEncoder) formatter;
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        // the Formatter interface was changed in logback 1.x, but our ECS implementation is compatible with both
        // older and newer versions of the API so we can rely on the more recent version of the API
        tracer.reportLog(formatter.encode(eventObject));
    }
}
