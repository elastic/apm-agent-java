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
package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JmxConfiguration extends ConfigurationOptionProvider implements co.elastic.apm.agent.tracer.configuration.JmxConfiguration {

    private ConfigurationOption<List<JmxMetric>> captureJmxMetrics = ConfigurationOption.<List<JmxMetric>>builder(JmxMetric.TokenValueConverter.INSTANCE, List.class)
        .key("capture_jmx_metrics")
        .tags("added[1.11.0]")
        .description("Report metrics from JMX to the APM Server\n" +
            "\n" +
            "Can contain multiple comma separated JMX metric definitions:\n" +
            "\n" +
            "----\n" +
            "object_name[<JMX object name pattern>] attribute[<JMX attribute>:metric_name=<optional metric name>]\n" +
            "----\n" +
            "\n" +
            "* `object_name`:\n" +
            "+\n" +
            "For more information about the JMX object name pattern syntax,\n" +
            "see the https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[`ObjectName` Javadocs].\n" +
            "* `attribute`:\n" +
            "+\n" +
            "The name of the JMX attribute.\n" +
            "The JMX value has to be either a `Number` or a composite where the composite items are numbers.\n" +
            "This element can be defined multiple times.\n" +
            "An attribute can contain optional properties.\n" +
            "The syntax for that is the same as for https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[`ObjectName`].\n" +
            "+\n" +
            "** `metric_name`:\n" +
            "+\n" +
            "A property within `attribute`.\n" +
            "This is the name under which the metric will be stored.\n" +
            "Setting this is optional and will be the same as the `attribute` if not set.\n" +
            "Note that all JMX metric names will be prefixed with `jvm.jmx.` by the agent.\n" +
            "\n" +
            "The agent creates `labels` for each link:https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html#getKeyPropertyList()[JMX key property] such as `type` and `name`.\n" +
            "\n" +
            "The link:https://docs.oracle.com/javase/7/docs/api/javax/management/ObjectName.html[JMX object name pattern] supports wildcards.\n" +
            "In this example, the agent will create a metricset for each memory pool `name` (such as `G1 Old Generation` and `G1 Young Generation`)\n" +
            "\n" +
            "----\n" +
            "object_name[java.lang:type=GarbageCollector,name=*] attribute[CollectionCount:metric_name=collection_count] attribute[CollectionTime]\n" +
            "----\n" +
            "\n" +
            "The resulting documents in Elasticsearch look similar to these (metadata omitted for brevity):\n" +
            "\n" +
            "[source,json]\n" +
            "----\n" +
            "{\n" +
            "  \"@timestamp\": \"2019-08-20T16:51:07.512Z\",\n" +
            "  \"jvm\": {\n" +
            "    \"jmx\": {\n" +
            "      \"collection_count\": 0,\n" +
            "      \"CollectionTime\":   0\n" +
            "    }\n" +
            "  },\n" +
            "  \"labels\": {\n" +
            "    \"type\": \"GarbageCollector\",\n" +
            "    \"name\": \"G1 Old Generation\"\n" +
            "  }\n" +
            "}\n" +
            "----\n" +
            "\n" +
            "[source,json]\n" +
            "----\n" +
            "{\n" +
            "  \"@timestamp\": \"2019-08-20T16:51:07.512Z\",\n" +
            "  \"jvm\": {\n" +
            "    \"jmx\": {\n" +
            "      \"collection_count\": 2,\n" +
            "      \"CollectionTime\":  11\n" +
            "    }\n" +
            "  },\n" +
            "  \"labels\": {\n" +
            "    \"type\": \"GarbageCollector\",\n" +
            "    \"name\": \"G1 Young Generation\"\n" +
            "  }\n" +
            "}\n" +
            "----\n" +
            "\n" +
            "\n" +
            "The agent also supports composite values for the attribute value.\n" +
            "In this example, `HeapMemoryUsage` is a composite value, consisting of `committed`, `init`, `used` and `max`.\n" +
            "----\n" +
            "object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=heap] \n" +
            "----\n" +
            "\n" +
            "The resulting documents in Elasticsearch look similar to this:\n" +
            "\n" +
            "[source,json]\n" +
            "----\n" +
            "{\n" +
            "  \"@timestamp\": \"2019-08-20T16:51:07.512Z\",\n" +
            "  \"jvm\": {\n" +
            "    \"jmx\": {\n" +
            "      \"heap\": {\n" +
            "        \"max\":      4294967296,\n" +
            "        \"init\":      268435456,\n" +
            "        \"committed\": 268435456,\n" +
            "        \"used\":       22404496\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"labels\": {\n" +
            "    \"type\": \"Memory\"\n" +
            "  }\n" +
            "}\n" +
            "----\n")
        .dynamic(true)
        .configurationCategory("JMX")
        .buildWithDefault(Collections.<JmxMetric>emptyList());

    private final ConcurrentMap<ChangeListener, ConfigurationOption.ChangeListener<List<JmxMetric>>> listeners = new ConcurrentHashMap<>();

    @Override
    public List<co.elastic.apm.agent.tracer.configuration.JmxConfiguration.JmxMetric> getCaptureJmxMetrics() {
        return Collections.unmodifiableList(captureJmxMetrics.get());
    }

    public ConfigurationOption<List<JmxMetric>> getCaptureJmxMetricsOption() {
        return captureJmxMetrics;
    }

    @Override
    public void addChangeListener(final ChangeListener changeListener) {
        ConfigurationOption.ChangeListener<List<JmxMetric>> listener = new ConfigurationOption.ChangeListener<>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                changeListener.onChange(oldValue, newValue);
            }
        };
        if (listeners.putIfAbsent(changeListener, listener) == null) {
            captureJmxMetrics.addChangeListener(listener);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
        ConfigurationOption.ChangeListener<List<JmxMetric>> listener = listeners.get(changeListener);
        if (listener != null) {
            captureJmxMetrics.removeChangeListener(listener);
        }
    }

    public static class JmxMetric implements co.elastic.apm.agent.tracer.configuration.JmxConfiguration.JmxMetric {

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

        @Override
        public ObjectName getObjectName() {
            return objectName;
        }

        @Override
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

        public static class Attribute implements co.elastic.apm.agent.tracer.configuration.JmxConfiguration.JmxMetric.Attribute {
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

            @Override
            public String getJmxAttributeName() {
                return jmxAttributeName;
            }

            @Override
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

    /**
     * Scans input like {@code map1_key1[value] map1_key2[value], map2_key1[value] map2_key2[value]} and converts it to a {@code List<Map<String, String>>}
     *
     * <p>
     * For example, the following assertion is valid:
     * </p>
     * {@code assert new MapsTokenScanner("foo[bar] baz[qux], quux[corge]").equals(List.of(Map.of("foo", "bar", "baz", "qux"), Map.of("quux", "corge"))}
     */
    public static class MapsTokenScanner {
        private final String input;
        private int pos; // read position char offset
        private int valueOpenSquareBracket; // allows square brackets within values

        public MapsTokenScanner(String input) {
            this.input = input;
        }

        public static String toTokenString(List<Map<String, List<String>>> maps) {
            if (maps.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, List<String>> map : maps) {
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    for (String value : entry.getValue()) {
                        sb.append(entry.getKey()).append('[').append(value).append(']').append(" ");
                    }
                }
                if (!map.isEmpty()) {
                    // remove last ' '
                    sb.setLength(sb.length() - 1);
                }
                sb.append(", ");
            }
            // remove last ', '
            sb.setLength(sb.length() - 2);

            return sb.toString();
        }

        public List<Map<String, String>> scanMaps() {
            skipWhiteSpace();
            List<Map<String, String>> maps = new ArrayList<>();
            while (hasNext()) {
                maps.add(scanMap());
            }
            return maps;
        }

        public List<Map<String, List<String>>> scanMultiValueMaps() {
            skipWhiteSpace();
            List<Map<String, List<String>>> maps = new ArrayList<>();
            while (hasNext()) {
                maps.add(scanMultiValueMap());
            }
            return maps;
        }

        public Map<String, List<String>> scanMultiValueMap() {
            Map<String, List<String>> map = new HashMap<>();
            skipWhiteSpace();
            while (hasNext()) {
                if (peek() == ',') {
                    next();
                    break;
                }
                String key = scanKey();
                if (!map.containsKey(key)) {
                    map.put(key, new ArrayList<String>());
                }
                map.get(key).add(scanValue());
                skipWhiteSpace();
            }
            return map;
        }

        public Map<String, String> scanMap() {
            Map<String, String> map = new HashMap<>();
            skipWhiteSpace();
            while (hasNext()) {
                if (peek() == ',') {
                    next();
                    break;
                }
                map.put(scanKey(), scanValue());
                skipWhiteSpace();
            }
            return map;
        }

        public String scanKey() {
            if (next() == '[') {
                throw new IllegalArgumentException("Empty key at pos " + pos + " in '" + input + "'");
            }
            int start = pos - 1;
            while (!isNext('[')) {
                if (!hasNext()) {
                    throw new IllegalArgumentException("Expected value start token '[' at pos " + pos + " in '" + input + "'");
                }
                next();
            }
            return input.substring(start, pos);
        }

        public String scanValue() {
            if (!isNext('[')) {
                throw new IllegalArgumentException("Expected value start token '[' at pos " + pos + " in '" + input + "'");
            } else {
                valueOpenSquareBracket++;
                next();
            }
            int start = pos;
            while (valueOpenSquareBracket > 0) {
                if (isNext('[')) {
                    valueOpenSquareBracket++;
                } else if (isNext(']')) {
                    valueOpenSquareBracket--;
                }
                if (!hasNext()) {
                    throw new IllegalArgumentException("Expected end value token ']' at pos " + pos + " in '" + input + "'");
                }
                next();
            }
            if (pos > 0) {
                return input.substring(start, pos-1);
            }
            throw new IllegalArgumentException("Empty values are not allowed");
        }

        public void skipWhiteSpace() {
            while (hasNext() && Character.isWhitespace(peek())) {
                next();
            }
        }

        public boolean isNext(char c) {
            return hasNext() && peek() == c;
        }

        public char next() {
            return input.charAt(pos++);
        }

        public char peek() {
            return input.charAt(pos);
        }

        public boolean hasNext() {
            return pos < input.length();
        }
    }
}
