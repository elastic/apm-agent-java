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
package co.elastic.apm.agent.jmx;

import org.stagemonitor.configuration.converter.AbstractValueConverter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JmxMetric {

    private static final String OBJECT_NAME = "object_name";
    private static final String ATTRIBUTE = "attribute";
    private static final String METRIC_NAME = "metric_name";
    private final ObjectName objectName;
    private final List<Attribute> attributes;

    private JmxMetric(ObjectName objectName, List<Attribute> attributes) {
        this.objectName = objectName;
        this.attributes = attributes;
    }

    public static JmxMetric valueOf(String s) {
        return TokenValueConverter.INSTANCE.convert(s).get(0);
    }

    @Nonnull
    private static JmxMetric fromMap(Map<String, List<String>> map) {
        if (!map.containsKey(OBJECT_NAME)) {
            throw new IllegalArgumentException("object_name is missing");
        }
        if (!map.containsKey(ATTRIBUTE)) {
            throw new IllegalArgumentException("attribute is missing");
        }
        Set<String> unknownKeys = new HashSet<>(map.keySet());
        unknownKeys.removeAll(Arrays.asList(OBJECT_NAME, ATTRIBUTE));
        if (!unknownKeys.isEmpty()) {
            throw new IllegalArgumentException("Unknown keys: " + unknownKeys);
        }

        String objectNameString = map.get(OBJECT_NAME).get(0);
        ObjectName objectName;
        try {
            objectName = new ObjectName(objectNameString);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid syntax for object_name[" + objectNameString + "] (" + e.getMessage() + ")", e);
        }
        List<Attribute> attributes = new ArrayList<>();
        for (String attribute : map.get(ATTRIBUTE)) {
            attributes.add(Attribute.valueOf(attribute));
        }
        return new JmxMetric(objectName, attributes);
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return TokenValueConverter.INSTANCE.toString(Collections.singletonList(this));
    }

    Map<String, List<String>> asMap() {
        HashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put(OBJECT_NAME, Collections.singletonList(objectName.toString()));
        ArrayList<String> attributeStrings = new ArrayList<>();
        for (Attribute attribute : this.attributes) {
            attributeStrings.add(attribute.toString());
        }
        map.put(ATTRIBUTE, attributeStrings);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JmxMetric jmxMetric = (JmxMetric) o;
        return objectName.equals(jmxMetric.objectName) &&
            attributes.equals(jmxMetric.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectName, attributes);
    }

    public static class TokenValueConverter extends AbstractValueConverter<List<JmxMetric>> {

        public final static TokenValueConverter INSTANCE = new TokenValueConverter();

        @Override
        public List<JmxMetric> convert(String s) throws IllegalArgumentException {
            List<JmxMetric> result = new ArrayList<>();
            List<Map<String, List<String>>> maps = new MapsTokenScanner(s).scanMultiValueMaps();
            for (Map<String, List<String>> map : maps) {
                result.add(JmxMetric.fromMap(map));
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public String toString(List<JmxMetric> value) {
            List<Map<String, List<String>>> maps = new ArrayList<>();
            for (JmxMetric jmxMetric : value) {
                maps.add(jmxMetric.asMap());
            }
            return MapsTokenScanner.toTokenString(maps);
        }

    }

    public static class Attribute {
        public static final String IGNORE = "ignore";
        private final String stringRepresentation;
        private final String jmxAttributeName;
        @Nullable
        private final String metricName;

        public static Attribute valueOf(final String s) {
            try {
                ObjectName objectName;
                if (!s.contains(":")) {
                    // ObjectNames require to have at least one key property
                    // let's fake it
                    objectName = new ObjectName(s, IGNORE, "this");
                } else {
                    objectName = new ObjectName(s);
                }
                Set<String> unknownProperties = new HashSet<>(objectName.getKeyPropertyList().keySet());
                unknownProperties.removeAll(Arrays.asList(IGNORE, METRIC_NAME));
                if (!unknownProperties.isEmpty()) {
                    throw new IllegalArgumentException("Unknown properties: " + unknownProperties);
                }
                return new Attribute(s, objectName.getDomain(), objectName.getKeyProperty(METRIC_NAME));
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException("Invalid syntax for attribute[" + s + "] (" + e.getMessage() + ")", e);
            }
        }

        private Attribute(String stringRepresentation, String jmxAttributeName, @Nullable String metricName) {
            this.stringRepresentation = stringRepresentation;
            this.jmxAttributeName = jmxAttributeName;
            this.metricName = metricName;
        }

        public String getJmxAttributeName() {
            return jmxAttributeName;
        }

        public String getMetricName() {
            return metricName != null ? metricName : jmxAttributeName;
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Attribute attribute = (Attribute) o;
            return jmxAttributeName.equals(attribute.jmxAttributeName) &&
                Objects.equals(metricName, attribute.metricName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jmxAttributeName, metricName);
        }
    }
}
