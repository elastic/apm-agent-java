/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JmxMetric {

    private static final String OBJECT_NAME = "object_name";
    private static final String ATTRIBUTE = "attribute";
    private static final String METRIC_NAME = "metric_name";
    private final ObjectName objectName;
    private final String attribute;
    @Nullable
    private final String metricName;

    public JmxMetric(String objectName, String attribute, @Nullable String metricName) {
        try {
            this.objectName = new ObjectName(objectName);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        this.attribute = attribute;
        this.metricName = metricName;
    }

    public static JmxMetric valueOf(String s) {
        return TokenValueConverter.INSTANCE.convert(s).get(0);
    }

    @Nonnull
    private static JmxMetric fromMap(Map<String, ?> map) {
        if (!map.containsKey(OBJECT_NAME)) {
            throw new IllegalArgumentException("object_name is missing");
        }
        if (!map.containsKey(ATTRIBUTE)) {
            throw new IllegalArgumentException("attribute is missing");
        }
        return new JmxMetric(map.get(OBJECT_NAME).toString(), map.get(ATTRIBUTE).toString(), Objects.toString(map.get(METRIC_NAME), null));
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public String getAttribute() {
        return attribute;
    }

    public String getMetricName() {
        return metricName != null ? metricName : attribute;
    }

    @Override
    public String toString() {
        return TokenValueConverter.INSTANCE.toString(Collections.singletonList(this));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JmxMetric jmxMetric = (JmxMetric) o;
        return objectName.equals(jmxMetric.objectName) &&
            attribute.equals(jmxMetric.attribute) &&
            Objects.equals(metricName, jmxMetric.metricName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectName, attribute, metricName);
    }

    Map<String, String> asMap() {
        HashMap<String, String> map = new LinkedHashMap<>();
        map.put(OBJECT_NAME, objectName.toString());
        map.put(ATTRIBUTE, attribute);
        if (metricName != null) {
            map.put(METRIC_NAME, metricName);
        }
        return map;
    }

    public static class TokenValueConverter extends AbstractValueConverter<List<JmxMetric>> {

        public final static TokenValueConverter INSTANCE = new TokenValueConverter();

        @Override
        public List<JmxMetric> convert(String s) throws IllegalArgumentException {
            List<JmxMetric> result = new ArrayList<>();
            List<Map<String, String>> maps = new MapsTokenScanner(s).scanMaps();
            for (Map<String, String> map : maps) {
                result.add(JmxMetric.fromMap(map));
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public String toString(List<JmxMetric> value) {
            List<Map<String, String>> maps = new ArrayList<>();
            for (JmxMetric jmxMetric : value) {
                maps.add(jmxMetric.asMap());
            }
            return MapsTokenScanner.toTokenString(maps);
        }

    }
}
