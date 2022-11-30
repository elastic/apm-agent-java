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
package co.elastic.apm.agent.log4j2.shipper;

import co.elastic.apm.agent.report.Reporter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.AbstractAppender;

public class Log4j2LogShipperAppender extends AbstractAppender {

    private final Reporter reporter;
    private final StringLayout ecsLayout;

    public Log4j2LogShipperAppender(Reporter reporter, StringLayout ecsLayout) {
        super("ElasticApmAppender", null, ecsLayout, true, null);
        this.reporter = reporter;
        this.ecsLayout = ecsLayout;
    }

    @Override
    public void append(LogEvent event) {
        reporter.reportLog(ecsLayout.toSerializable(event));
    }

}
