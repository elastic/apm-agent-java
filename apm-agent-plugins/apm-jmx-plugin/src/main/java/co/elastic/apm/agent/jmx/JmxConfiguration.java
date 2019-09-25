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

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collections;
import java.util.List;

public class JmxConfiguration extends ConfigurationOptionProvider {

    private ConfigurationOption<List<JmxMetric>> captureJmxMetrics = ConfigurationOption.<List<JmxMetric>>builder(JmxMetric.TokenValueConverter.INSTANCE, List.class)
        .key("capture_jmx_metrics")
        .tags("added[1.10.0]")
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

    ConfigurationOption<List<JmxMetric>> getCaptureJmxMetrics() {
        return captureJmxMetrics;
    }
}
