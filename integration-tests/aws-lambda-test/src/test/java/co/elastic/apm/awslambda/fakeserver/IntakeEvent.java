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
package co.elastic.apm.awslambda.fakeserver;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;

public class IntakeEvent extends ServerEvent {

    private static final ObjectMapper jsonParser = new ObjectMapper();
    private final IntakeRequest originRequest;

    private final String eventJson;

    private final ObjectNode event;

    public IntakeEvent(IntakeRequest request, String eventJson) {
        this.originRequest = request;
        this.eventJson = eventJson;
        try {
            this.event = jsonParser.readValue(eventJson, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid event json");
        }
        request.addEvent(this);
    }

    public String getEventType() {
        Iterator<String> it = event.fieldNames();
        if (!it.hasNext()) {
            throw new IllegalStateException("Event is empty: " + eventJson);
        }
        String name = it.next();
        if (it.hasNext()) {
            throw new IllegalStateException("Event has multiple properties: " + event);
        }
        return name;
    }

    public boolean isTransaction() {
        return "transaction".equals(getEventType());
    }

    public boolean isMetadata() {
        return "metadata".equals(getEventType());
    }

    public ObjectNode getContent() {
        return (ObjectNode) event.get(getEventType());
    }

    public ObjectNode getMetadata() {
        IntakeEvent metadataEvent = originRequest.getEvents().get(0);
        if (!metadataEvent.isMetadata()) {
            throw new IllegalStateException("Intake request did not receive metadata as first event");
        }
        return (ObjectNode) metadataEvent.getContent();
    }

    @Override
    public String toString() {
        return "IntakeEvent{" + eventJson + '}';
    }
}
