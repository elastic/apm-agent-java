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
package co.elastic.apm.agent.jmx;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.util.GlobalLocks;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.relation.MBeanServerNotificationFilter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JmxMetricTracker extends AbstractLifecycleListener {

    private static final String JMX_PREFIX = "jvm.jmx.";
    private static final Logger logger = LoggerFactory.getLogger(JmxMetricTracker.class);
    @Nullable
    private volatile Thread logManagerPropertyPoller;
    @Nullable
    private volatile MBeanServer server;
    private final JmxConfiguration jmxConfiguration;
    private final MetricRegistry metricRegistry;
    @Nullable
    private volatile NotificationListener listener;

    public JmxMetricTracker(ElasticApmTracer tracer) {
        jmxConfiguration = tracer.getConfig(JmxConfiguration.class);
        metricRegistry = tracer.getMetricRegistry();
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        ConfigurationOption.ChangeListener<List<JmxMetric>> initChangeListener = new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                if (oldValue.isEmpty() && !newValue.isEmpty()) {
                    tryInit();
                }
            }
        };
        // adding change listener before checking if option is not empty to avoid missing an update due to a race condition
        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(initChangeListener);
        if (!jmxConfiguration.getCaptureJmxMetrics().get().isEmpty()) {
            tryInit();
            jmxConfiguration.getCaptureJmxMetrics().removeChangeListener(initChangeListener);
        } else {
            logger.debug("Deferring initialization of JMX metric tracking until capture_jmx_metrics is set.");
        }
    }

    private synchronized void tryInit() {
        if (this.server != null || this.logManagerPropertyPoller != null) {
            return;
        }
        // Avoid creating the platform MBean server, only get it if already initialized
        // otherwise WildFly fails to start with a IllegalStateException:
        // WFLYLOG0078: The logging subsystem requires the log manager to be org.jboss.logmanager.LogManager
        if (setsCustomLogManager()) {
            List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
            if (!servers.isEmpty()) {
                // platform MBean server is already initialized
                init(servers.get(0));
            } else {
                deferInit();
            }
        } else {
            init(getPlatformMBeanServerThreadSafely());
        }
    }

    private MBeanServer getPlatformMBeanServerThreadSafely() {
        GlobalLocks.JUL_INIT_LOCK.lock();
        try {
            return ManagementFactory.getPlatformMBeanServer();
        } finally {
            GlobalLocks.JUL_INIT_LOCK.unlock();
        }
    }

    private void deferInit() {
        logger.debug("Deferring initialization of JMX metric tracking until log manager is initialized");
        Thread thread = new Thread(new Runnable() {

            private final long timeout = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);

            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted() || timeout <= System.currentTimeMillis()) {
                    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                    if (!servers.isEmpty()) {
                        // avoid actively creating a platform mbean server
                        // because JBoss sets the javax.management.builder.initial system property
                        // this makes Java create a JBoss specific mbean server that can only be loaded
                        // when the module class loader is set as the current thread's context class loader
                        init(servers.get(0));
                        return;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        thread.setName("elastic-apm-jmx-init");
        thread.setDaemon(true);
        thread.start();
        logManagerPropertyPoller = thread;
    }

    private boolean setsCustomLogManager() {
        return ClassLoader.getSystemClassLoader().getResource("org/jboss/modules/Main.class") != null;
    }

    synchronized void init(final MBeanServer platformMBeanServer) {
        if (this.server != null) {
            return;
        }
        logger.debug("Init JMX metric tracking with server {}", platformMBeanServer);
        this.server = platformMBeanServer;
        registerMBeanNotificationListener(platformMBeanServer);

        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                List<JmxMetricRegistration> oldRegistrations = compileJmxMetricRegistrations(oldValue, platformMBeanServer);
                List<JmxMetricRegistration> newRegistrations = compileJmxMetricRegistrations(newValue, platformMBeanServer);

                for (JmxMetricRegistration addedRegistration : removeAll(oldRegistrations, newRegistrations)) {
                    addedRegistration.register(platformMBeanServer, metricRegistry);
                }
                for (JmxMetricRegistration deletedRegistration : removeAll(newRegistrations, oldRegistrations)) {
                    deletedRegistration.unregister(metricRegistry);
                }

            }
        });
        register(jmxConfiguration.getCaptureJmxMetrics().get(), platformMBeanServer);
    }

    private void registerMBeanNotificationListener(final MBeanServer server) {
        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        filter.enableAllObjectNames();
        filter.enableType(MBeanServerNotification.REGISTRATION_NOTIFICATION);
        listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                try {
                    if (notification instanceof MBeanServerNotification) {
                        onMBeanAdded(((MBeanServerNotification) notification).getMBeanName());
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            private void onMBeanAdded(ObjectName mBeanName) {
                logger.trace("Receiving MBean registration notification for {}", mBeanName);
                for (JmxMetric jmxMetric : jmxConfiguration.getCaptureJmxMetrics().get()) {
                    ObjectName metricName = jmxMetric.getObjectName();
                    if (metricName.apply(mBeanName) || matchesJbossStatisticsPool(mBeanName, metricName, server)) {
                        logger.debug("MBean added at runtime: {}", jmxMetric.getObjectName());
                        register(Collections.singletonList(jmxMetric), server);
                    }
                }
            }

            private boolean matchesJbossStatisticsPool(ObjectName beanName, ObjectName metricName, MBeanServer server) {
                String asDomain = "jboss.as";
                String exprDomain = "jboss.as.expr";

                if (!asDomain.equals(metricName.getDomain())) {
                    // only relevant for metrics in 'jboss.as' domain
                    return false;
                }

                if (!exprDomain.equals(beanName.getDomain()) && !asDomain.equals(beanName.getDomain())) {
                    // only relevant for notifications in the 'jboss.as' or 'jboss.as.expr' domains
                    return false;
                }

                // On JBoos EAP 7.3.0 and 7.1.0, we have similar behaviors
                // - notification can be on `jboss.as.expr` or `jboss.as` domain, but we registered `jboss.as`
                // - notification on `jboss.as.expr` seems to be setup-dependant and might be optional
                // - notification bean will miss the `statistics=pool` property
                // - when we get one of those "close but not 100% identical", the beans we usually look for are available
                //
                // We just do an extra lookup to check that the MBean we are looking for is actually present
                // thus in the worst case it just means few extra lookups.
                //
                // while we haven't found a clear "why is this not reflected on JMX notifications", some
                // references to this can be found in Jboss/Wildfly sources with the following regex: `statistics.*pool`

                try {
                    Hashtable<String, String> metricProperties = metricName.getKeyPropertyList();
                    Hashtable<String, String> beanProperties = beanName.getKeyPropertyList();
                    if ("pool".equals(metricProperties.get("statistics")) && !beanProperties.containsKey("statistics")) {
                        beanProperties.put("statistics", "pool");
                    }

                    ObjectName newName = new ObjectName(asDomain, beanProperties);
                    boolean matches = metricName.apply(newName) && server.queryMBeans(newName, null).size() > 0;
                    if (matches) {
                        logger.debug("JBoss fallback detection applied for {} MBean metric registration", newName);
                    }
                    return matches;
                } catch (MalformedObjectNameException e) {
                    return false;
                }
            }

        };

        try {
            server.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static <T> List<T> removeAll(List<T> removeFromThis, List<T> toRemove) {
        List<T> result = new ArrayList<T>(toRemove);
        result.removeAll(removeFromThis);
        return result;
    }

    private void register(List<JmxMetric> jmxMetrics, MBeanServer server) {
        for (JmxMetricRegistration registration : compileJmxMetricRegistrations(jmxMetrics, server)) {
            registration.register(server, metricRegistry);
        }
    }

    /**
     * A single {@link JmxMetric} can yield multiple {@link JmxMetricRegistration}s if the {@link JmxMetric} contains multiple {@link JmxMetric#attributes}
     */
    private List<JmxMetricRegistration> compileJmxMetricRegistrations(List<JmxMetric> jmxMetrics, MBeanServer server) {
        List<JmxMetricRegistration> registrations = new ArrayList<>();
        for (JmxMetric jmxMetric : jmxMetrics) {
            try {
                addJmxMetricRegistration(jmxMetric, registrations, server);
            } catch (Exception e) {
                logger.error("Failed to register JMX metric {}", jmxMetric.toString(), e);
            }
        }
        return registrations;
    }

    private void addJmxMetricRegistration(final JmxMetric jmxMetric, List<JmxMetricRegistration> registrations, MBeanServer server) throws JMException {
        Set<ObjectInstance> mbeans = server.queryMBeans(jmxMetric.getObjectName(), null);
        if (!mbeans.isEmpty()) {
            logger.debug("Found mbeans for object name {}", jmxMetric.getObjectName());
        } else {
            logger.debug("Found no mbeans for object name {}. Listening for mbeans added later.", jmxMetric.getObjectName());
        }
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
                    } catch (InstanceNotFoundException | AttributeNotFoundException e) {
                        unregister(metricRegistry);
                        return Double.NaN;
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

    @Override
    public void stop() throws Exception {
        MBeanServer server = this.server;
        NotificationListener listener = this.listener;
        if (server != null && listener != null) {
            server.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener);
        }
        Thread logManagerPropertyPoller = this.logManagerPropertyPoller;
        if (logManagerPropertyPoller != null) {
            logManagerPropertyPoller.interrupt();
        }
    }
}
