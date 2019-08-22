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

import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.lang.management.ManagementFactory;
import java.util.List;

public class JmxMetricTracker implements LifecycleListener {

    private static final String JMX_PREFIX = "jvm.jmx.";
    private final Logger logger;
    private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final JmxConfiguration jmxConfiguration;
    private final MetricRegistry metricRegistry;

    public JmxMetricTracker(ElasticApmTracer tracer) {
        this(tracer, LoggerFactory.getLogger(JmxMetricTracker.class));
    }

    public JmxMetricTracker(ElasticApmTracer tracer, Logger logger) {
        this.logger = logger;
        jmxConfiguration = tracer.getConfig(JmxConfiguration.class);
        metricRegistry = tracer.getMetricRegistry();
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                registerJmxMetrics(newValue);
            }
        });
        registerJmxMetrics(jmxConfiguration.getCaptureJmxMetrics().get());
    }

    private void registerJmxMetrics(List<JmxMetric> jmxMetrics) {
        for (JmxMetric jmxMetric : jmxMetrics) {
            try {
                registerJmxMetric(jmxMetric);
            } catch (Exception e) {
                logger.error("Failed to register JMX metric {}", jmxMetric.toString(), e);
            }
        }
    }

    private void registerJmxMetric(final JmxMetric jmxMetric) throws JMException {
        for (ObjectInstance mbean : server.queryMBeans(jmxMetric.getObjectName(), null)) {
            for (JmxMetric.Attribute attribute : jmxMetric.getAttributes()) {
                final ObjectName objectName = mbean.getObjectName();
                final Object value = server.getAttribute(objectName, attribute.getJmxAttributeName());
                if (value instanceof Number) {
                    addScalarMetric(attribute, objectName);
                } else if (value instanceof CompositeData) {
                    final CompositeData compositeValue = (CompositeData) value;
                    for (final String key : compositeValue.getCompositeType().keySet()) {
                        if (compositeValue.get(key) instanceof Number) {
                            addCompositeMetric(attribute, objectName, key);
                        } else {
                            logger.warn("Can't create metric '{}' because composite value '{}' is not a number: '{}'", jmxMetric, key, value);
                        }
                    }
                } else {
                    logger.warn("Can't create metric '{}' because attribute '{}' is not a number: '{}'", jmxMetric, attribute.getJmxAttributeName(), value);
                }
            }
        }
    }

    private void addScalarMetric(final JmxMetric.Attribute attribute, final ObjectName objectName) {
        metricRegistry.add(JMX_PREFIX + attribute.getMetricName(), Labels.Mutable.of(objectName.getKeyPropertyList()), new DoubleSupplier() {
            @Override
            public double get() {
                try {
                    return ((Number) server.getAttribute(objectName, attribute.getJmxAttributeName())).doubleValue();
                } catch (Exception e) {
                    return Double.NaN;
                }
            }
        });
    }

    private void addCompositeMetric(final JmxMetric.Attribute attribute, final ObjectName objectName, final String key) {
        metricRegistry.add(JMX_PREFIX + attribute.getMetricName() + "." + key, Labels.Mutable.of(objectName.getKeyPropertyList()), new DoubleSupplier() {
            @Override
            public double get() {
                try {
                    return ((Number) ((CompositeData) server.getAttribute(objectName, attribute.getJmxAttributeName())).get(key)).doubleValue();
                } catch (Exception e) {
                    return Double.NaN;
                }
            }
        });
    }

    @Override
    public void stop() {

    }
}
