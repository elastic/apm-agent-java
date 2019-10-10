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

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.openmbean.CompositeData;
import javax.management.relation.MBeanServerNotificationFilter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class JmxMetricTracker implements LifecycleListener {

    private static final String JMX_PREFIX = "jvm.jmx.";
    private final Logger logger;
    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    private final JmxConfiguration jmxConfiguration;
    private final MetricRegistry metricRegistry;
    private final CopyOnWriteArrayList<NotificationListener> registeredListeners = new CopyOnWriteArrayList<>();

    public JmxMetricTracker(ElasticApmTracer tracer) {
        this(tracer, LoggerFactory.getLogger(JmxMetricTracker.class));
    }

    JmxMetricTracker(ElasticApmTracer tracer, Logger logger) {
        this.logger = logger;
        jmxConfiguration = tracer.getConfig(JmxConfiguration.class);
        metricRegistry = tracer.getMetricRegistry();
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                removeListeners();
                List<JmxMetricRegistration> oldRegistrations = compileJmxMetricRegistrations(oldValue, new ArrayList<JmxMetric>());
                ArrayList<JmxMetric> notFound = new ArrayList<>();
                List<JmxMetricRegistration> newRegistrations = compileJmxMetricRegistrations(newValue, notFound);

                List<JmxMetricRegistration> addedRegistrations = new ArrayList<>(newRegistrations);
                addedRegistrations.removeAll(oldRegistrations);

                List<JmxMetricRegistration> deletedRegistrations = new ArrayList<>(oldRegistrations);
                deletedRegistrations.removeAll(newRegistrations);

                for (JmxMetricRegistration addedRegistration : addedRegistrations) {
                    addedRegistration.register(server, metricRegistry);
                }

                for (JmxMetricRegistration deletedRegistration : deletedRegistrations) {
                    deletedRegistration.unregister(metricRegistry);
                }

                for (JmxMetric jmxMetric : notFound) {
                    registerListenerForNotFoundMBean(jmxMetric);
                }
            }
        });
        register(jmxConfiguration.getCaptureJmxMetrics().get());
    }

    private void register(List<JmxMetric> jmxMetrics) {
        List<JmxMetric> notFound = new ArrayList<>();
        for (JmxMetricRegistration registration : compileJmxMetricRegistrations(jmxMetrics, notFound)) {
            registration.register(server, metricRegistry);
        }
        for (JmxMetric jmxMetric : notFound) {
            registerListenerForNotFoundMBean(jmxMetric);
        }
    }

    private void removeListeners() {
        if (registeredListeners.isEmpty()) {
            return;
        }
        logger.debug("Removing MBean listeners");
        // remove previously registered listeners so that they are not registered again if the mbean is still missing
        for (NotificationListener registeredListener : registeredListeners) {
            removeListener(registeredListener);
        }
    }

    private void removeListener(NotificationListener registeredListener) {
        try {
            server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, registeredListener);
        } catch (OperationsException e) {
            logger.error(e.getMessage(), e);
        }
        registeredListeners.remove(registeredListener);
    }

    /**
     * A single {@link JmxMetric} can yield multiple {@link JmxMetricRegistration}s if the {@link JmxMetric} contains multiple {@link JmxMetric#attributes}
     */
    private List<JmxMetricRegistration> compileJmxMetricRegistrations(List<JmxMetric> jmxMetrics, List<JmxMetric> notFound) {
        List<JmxMetricRegistration> registrations = new ArrayList<>();
        for (JmxMetric jmxMetric : jmxMetrics) {
            try {
                if (!addJmxMetricRegistration(jmxMetric, registrations)) {
                    notFound.add(jmxMetric);
                }
            } catch (Exception e) {
                logger.error("Failed to register JMX metric {}", jmxMetric.toString(), e);
            }
        }
        return registrations;
    }

    private void registerListenerForNotFoundMBean(final JmxMetric jmxMetric) {
        logger.info("Could not find mbeans for {}. Adding a listener in case the mbean is registered at a later point.", jmxMetric.getObjectName());
        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        filter.enableObjectName(jmxMetric.getObjectName());
        NotificationListener listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                logger.debug("MBean added at runtime: {}", jmxMetric.getObjectName());
                removeListener(this);
                if (jmxConfiguration.getCaptureJmxMetrics().get().contains(jmxMetric)) {
                    register(Collections.singletonList(jmxMetric));
                }
            }
        };
        try {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
            registeredListeners.add(listener);
        } catch (InstanceNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private boolean addJmxMetricRegistration(final JmxMetric jmxMetric, List<JmxMetricRegistration> registrations) throws JMException {
        Set<ObjectInstance> mbeans = server.queryMBeans(jmxMetric.getObjectName(), null);
        if (mbeans.isEmpty()) {
            return false;
        }
        logger.debug("Found mbeans for object name {}", jmxMetric.getObjectName());
        for (ObjectInstance mbean : mbeans) {
            for (JmxMetric.Attribute attribute : jmxMetric.getAttributes()) {
                final ObjectName objectName = mbean.getObjectName();
                final Object value;
                try {
                    value = server.getAttribute(objectName, attribute.getJmxAttributeName());
                    if (value instanceof Number) {
                        logger.debug("Found number attribute {}={}", attribute.getJmxAttributeName(), value);
                        registrations.add(new JmxMetricRegistration(JMX_PREFIX + attribute.getMetricName(),
                            Labels.Mutable.of(objectName.getKeyPropertyList()),
                            attribute.getJmxAttributeName(),
                            null,
                            objectName));
                    } else if (value instanceof CompositeData) {
                        final CompositeData compositeValue = (CompositeData) value;
                        for (final String key : compositeValue.getCompositeType().keySet()) {
                            if (compositeValue.get(key) instanceof Number) {
                                logger.debug("Found composite number attribute {}.{}={}", attribute.getJmxAttributeName(), key, value);
                                registrations.add(new JmxMetricRegistration(JMX_PREFIX + attribute.getMetricName() + "." + key,
                                    Labels.Mutable.of(objectName.getKeyPropertyList()),
                                    attribute.getJmxAttributeName(),
                                    key,
                                    objectName));
                            } else {
                                logger.warn("Can't create metric '{}' because composite value '{}' is not a number: '{}'", jmxMetric, key, value);
                            }
                        }
                    } else {
                        logger.warn("Can't create metric '{}' because attribute '{}' is not a number: '{}'", jmxMetric, attribute.getJmxAttributeName(), value);
                    }
                } catch (AttributeNotFoundException e) {
                    logger.warn("Can't create metric '{}' because attribute '{}' could not be found", jmxMetric, attribute.getJmxAttributeName());
                }
            }
        }
        return true;
    }


    static class JmxMetricRegistration {
        private static final Logger logger = LoggerFactory.getLogger(JmxMetricRegistration.class);
        private final String metricName;
        private final Labels.Immutable labels;
        private final String jmxAttribute;
        @Nullable
        private final String compositeDataKey;
        private final ObjectName objectName;

        private JmxMetricRegistration(String metricName, Labels labels, String jmxAttribute, @Nullable String compositeDataKey, ObjectName objectName) {
            this.metricName = metricName;
            this.labels = labels.immutableCopy();
            this.jmxAttribute = jmxAttribute;
            this.compositeDataKey = compositeDataKey;
            this.objectName = objectName;
        }


        void register(final MBeanServer server, final MetricRegistry metricRegistry) {
            logger.debug("Registering JMX metric {} {}.{} as metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            metricRegistry.add(metricName, labels, new DoubleSupplier() {
                @Override
                public double get() {
                    try {
                        if (compositeDataKey == null) {
                            return ((Number) server.getAttribute(objectName, jmxAttribute)).doubleValue();
                        } else {
                            return ((Number) ((CompositeData) server.getAttribute(objectName, jmxAttribute)).get(compositeDataKey)).doubleValue();
                        }
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }
            });
        }

        void unregister(MetricRegistry metricRegistry) {
            logger.debug("Unregistering JMX metric {} {}.{} metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            metricRegistry.removeGauge(metricName, labels);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JmxMetricRegistration that = (JmxMetricRegistration) o;
            return metricName.equals(that.metricName) &&
                labels.equals(that.labels);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metricName, labels);
        }
    }

    List<NotificationListener> getRegisteredListeners() {
        return registeredListeners;
    }

    @Override
    public void stop() {
        removeListeners();
    }
}
