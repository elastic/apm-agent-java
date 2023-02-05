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
package co.elastic.apm.agent.report;

public interface ReporterMonitor {

    void eventCreated(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSize);

    void eventDequeued(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSize);

    void eventDroppedBeforeQueue(ReportingEvent.ReportingEventType eventType, long queueCapacity);

    void eventDroppedAfterDequeue(ReportingEvent.ReportingEventType eventType);

    void requestFinished(ReportingEventCounter requestContent, long acceptedEventCount, long bytesWritten, boolean success);


    ReporterMonitor NOOP = new ReporterMonitor() {

        @Override
        public void eventCreated(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSize) {

        }

        @Override
        public void eventDequeued(ReportingEvent.ReportingEventType eventType, long queueCapacity, long queueSize) {

        }

        @Override
        public void eventDroppedBeforeQueue(ReportingEvent.ReportingEventType eventType, long queueCapacity) {

        }

        @Override
        public void eventDroppedAfterDequeue(ReportingEvent.ReportingEventType eventType) {

        }

        @Override
        public void requestFinished(ReportingEventCounter contents, long acceptedEventCount, long bytesWritten, boolean success) {

        }
    };
}
