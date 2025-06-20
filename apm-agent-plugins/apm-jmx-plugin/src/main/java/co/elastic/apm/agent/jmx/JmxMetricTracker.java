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

import co.elastic.apm.agent.sdk.internal.util.ExecutorUtils;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.AbstractLifecycleListener;
import co.elastic.apm.agent.tracer.GlobalLocks;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import co.elastic.apm.agent.tracer.metrics.DoubleSupplier;
import co.elastic.apm.agent.tracer.metrics.Labels;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.relation.MBeanServerNotificationFilter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JmxMetricTracker extends AbstractLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(JmxMetricTracker.class);
    @Nullable
    private volatile Thread logManagerPropertyPoller;
    @Nullable
    private volatile MBeanServer server;
    private final Tracer tracer;
    private final JmxConfiguration jmxConfiguration;
    @Nullable
    private volatile NotificationListener listener;

    private final List<JmxMetric> failedMetrics;

    @Nullable
    private ScheduledExecutorService retryExecutor;

    public JmxMetricTracker(Tracer tracer) {
        this.tracer = tracer;
        jmxConfiguration = tracer.getConfig(JmxConfiguration.class);

        // using a synchronized list so adding to the list does not require synchronization
        failedMetrics = Collections.synchronizedList(new ArrayList<JmxMetric>());
    }

    @Override
    public void start(Tracer tracer) {
        ConfigurationOption.ChangeListener<List<JmxMetric>> captureJmxListener = new ConfigurationOption.ChangeListener<List<JmxMetric>>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, List<JmxMetric> oldValue, List<JmxMetric> newValue) {
                if (oldValue.isEmpty() && !newValue.isEmpty()) {
                    tryInit();
                }
            }
        };

        // adding change listener before checking if options are not empty to avoid missing an update due to a race condition
        jmxConfiguration.getCaptureJmxMetrics().addChangeListener(captureJmxListener);

        if (!jmxConfiguration.getCaptureJmxMetrics().get().isEmpty()) {
            tryInit();
            jmxConfiguration.getCaptureJmxMetrics().removeChangeListener(captureJmxListener);
        } else {
            logger.debug("Deferring initialization of JMX metric tracking until {} option is set", jmxConfiguration.getCaptureJmxMetrics().getKey());
        }
    }

    private synchronized void tryInit() {
        if (this.server != null || this.logManagerPropertyPoller != null) {
            return;
        }
        // Do not eagerly trigger creation of the platform MBean server for known problematic cases
        //
        // WildFly fails to start with a IllegalStateException:
        // WFLYLOG0078: The logging subsystem requires the log manager to be org.jboss.logmanager.LogManager
        //
        // JBoss sets the 'javax.management.builder.initial' system property, but uses the module classloader and
        // current thread context class loader to initialize it
        //
        // Weblogic sets the 'javax.management.builder.initial' system property at runtime
        if (setCustomPlatformMBeanServer()) {
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
        logger.debug("Deferring initialization of JMX metric tracking until platform mbean server is initialized");
        Thread thread = PrivilegedActionUtils.newThread(new Runnable() {

            private final long timeout = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);

            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted() || timeout <= System.currentTimeMillis()) {
                    List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                    if (!servers.isEmpty()) {
                        // avoid actively creating a platform mbean server
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

    private boolean setCustomPlatformMBeanServer() {
        return ClassLoader.getSystemClassLoader().getResource("org/jboss/modules/Main.class") != null
            || System.getProperty("weblogic.Name") != null || System.getProperty("weblogic.home") != null;
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
                List<JmxMetric> registrationErrors = new ArrayList<JmxMetric>(); // those are not needed
                List<JmxMetricRegistration> oldRegistrations = compileJmxMetricRegistrations(oldValue, platformMBeanServer, registrationErrors);

                List<JmxMetricRegistration> newRegistrations;
                synchronized (failedMetrics) {
                    failedMetrics.clear();
                    newRegistrations = compileJmxMetricRegistrations(newValue, platformMBeanServer, failedMetrics);
                }


                for (JmxMetricRegistration addedRegistration : removeAll(oldRegistrations, newRegistrations)) {
                    addedRegistration.register(platformMBeanServer, tracer);
                }
                for (JmxMetricRegistration deletedRegistration : removeAll(newRegistrations, oldRegistrations)) {
                    deletedRegistration.unregister(tracer);
                }
            }
        });

        ConfigurationOption<TimeDuration> failedRetryConfig = jmxConfiguration.getFaildRetryInterval();
        if (!failedRetryConfig.isDefault()) {
            long retryMillis = failedRetryConfig.getValue().getMillis();
            if (retryExecutor != null) {
                ExecutorUtils.shutdownAndWaitTermination(retryExecutor);
            }

            retryExecutor = ExecutorUtils.createSingleThreadSchedulingDaemonPool("jmx-retry");
            retryExecutor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    retryFailedJmx(platformMBeanServer);
                }
            }, retryMillis, retryMillis, TimeUnit.MILLISECONDS);
        }

        register(jmxConfiguration.getCaptureJmxMetrics().get(), platformMBeanServer, failedMetrics);
    }

    // package-private for testing
    void retryFailedJmx(MBeanServer platformMBeanServer) {
        List<JmxMetric> failed = JmxMetricTracker.this.failedMetrics;
        synchronized (failed) {
            List<JmxMetric> toRetry = new ArrayList<>(failed);
            failed.clear();
            register(toRetry, platformMBeanServer, failed);
        }
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
                    addMBean(mBeanName, jmxMetric);
                }
            }

            private void addMBean(ObjectName mBeanName, JmxMetric jmxMetric) {
                ObjectName metricName = jmxMetric.getObjectName();
                if (metricName.apply(mBeanName) || matchesJbossStatisticsPool(mBeanName, metricName, server)) {
                    logger.debug("MBean added at runtime: {}", jmxMetric.getObjectName());
                    register(Collections.singletonList(jmxMetric), server, failedMetrics);
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

    private void register(List<JmxMetric> jmxMetrics, MBeanServer server, List<JmxMetric> failedMetrics) {
        for (JmxMetricRegistration registration : compileJmxMetricRegistrations(jmxMetrics, server, failedMetrics)) {
            registration.register(server, tracer);
        }
    }

    /**
     * A single {@link JmxMetric} can yield multiple {@link JmxMetricRegistration}s if the {@link JmxMetric} contains multiple attributes
     *
     * @param jmxMetrics    JMX metrics to register
     * @param server        MBean server
     * @param failedMetrics list of JMX metrics that failed to register (out)
     */
    private List<JmxMetricRegistration> compileJmxMetricRegistrations(List<JmxMetric> jmxMetrics, MBeanServer server, List<JmxMetric> failedMetrics) {
        List<JmxMetricRegistration> globalRegistrations = new ArrayList<>();
        for (JmxMetric jmxMetric : jmxMetrics) {
            List<JmxMetricRegistration> metricRegistrations = new ArrayList<>();
            try {
                addJmxMetricRegistration(jmxMetric, metricRegistrations, server);
                globalRegistrations.addAll(metricRegistrations);
            } catch (Exception e) {
                failedMetrics.add(jmxMetric);
                logger.error("Failed to register JMX metric {}", jmxMetric.toString(), e);
            }

        }
        return globalRegistrations;
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
                final String metricPrepend = metricPrepend(attribute.getLabels(objectName));
                if (isWildcard(attribute)) {
                    MBeanInfo info = server.getMBeanInfo(objectName);
                    MBeanAttributeInfo[] attrInfo = info.getAttributes();
                    for (MBeanAttributeInfo attr : attrInfo) {
                        String attributeName = attr.getName();
                        tryAddJmxMetric(jmxMetric, registrations, server, attribute, objectName, attributeName, metricPrepend);
                    }
                } else {
                    String attributeName = attribute.getJmxAttributeName();
                    tryAddJmxMetric(jmxMetric, registrations, server, attribute, objectName, attributeName, null);
                }
            }
        }
    }

    private void tryAddJmxMetric(JmxMetric jmxMetric,
                                 List<JmxMetricRegistration> registrations,
                                 MBeanServer server,
                                 JmxMetric.Attribute attribute,
                                 ObjectName objectName,
                                 String attributeName,
                                 @Nullable String metricPrepend) throws MBeanException, InstanceNotFoundException, ReflectionException {

        try {
            Object value = server.getAttribute(objectName, attributeName);
            addJmxMetricRegistration(jmxMetric, registrations, objectName, value, attribute, attributeName, metricPrepend);
        } catch (AttributeNotFoundException e) {
            logger.warn("Can't create metric '{}' because attribute '{}' could not be found", jmxMetric, attributeName);
        } catch (RuntimeMBeanException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                // silently ignore this attribute, won't retry as it's not a transient runtime exception
            } else {
                throw e;
            }
        }
    }

    private static boolean isWildcard(JmxMetric.Attribute attribute) {
        return "*".equals(attribute.getJmxAttributeName());
    }

    private static String metricPrepend(Labels labels) {
        List<String> keys = labels.getKeys();
        for (int i = 0; i < keys.size(); i++) {
            if ("type".equals(keys.get(i))) {
                return labels.getValue(i) + ".";
            }
        }
        return "";
    }

    private void addJmxMetricRegistration(JmxMetric jmxMetric, List<JmxMetricRegistration> registrations, ObjectName objectName, Object value, JmxMetric.Attribute attribute, String attributeName, @Nullable String metricPrepend) throws AttributeNotFoundException {
        String effectiveAttributeName = metricPrepend == null ? attributeName : metricPrepend + attributeName;
        boolean unsubscribeOnError = jmxConfiguration.getFaildRetryInterval().isDefault();
        if (value instanceof Number) {
            logger.debug("Found number attribute {}={}", attribute.getJmxAttributeName(), value);
            registrations.add(
                new JmxMetricRegistration(
                    attribute.getMetricName(
                        effectiveAttributeName
                    ),
                    attribute.getLabels(objectName),
                    attributeName,
                    null,
                    objectName,
                    unsubscribeOnError
                )
            );
        } else if (value instanceof CompositeData) {
            final CompositeData compositeValue = (CompositeData) value;
            for (final String key : compositeValue.getCompositeType().keySet()) {
                Object entryValue = compositeValue.get(key);
                if (entryValue instanceof Number) {
                    logger.debug("Found composite number attribute {}.{}={}", attribute.getJmxAttributeName(), key, value);
                    registrations.add(
                        new JmxMetricRegistration(
                            attribute.getCompositeMetricName(
                                key,
                                effectiveAttributeName),
                            attribute.getLabels(objectName),
                            attributeName,
                            key,
                            objectName,
                            unsubscribeOnError
                        )
                    );
                } else {
                    if (!isWildcard(attribute)) {
                        logger.warn("Can't create metric '{}' because composite value '{}' is not a number: '{}'", jmxMetric, key, entryValue);
                    }
                }
            }
        } else {
            if (!isWildcard(attribute)) {
                logger.warn("Can't create metric '{}' because attribute '{}' is not a number: '{}'", jmxMetric, attributeName, value);
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
        private final boolean unsubscribeOnError;

        private JmxMetricRegistration(String metricName, Labels labels, String jmxAttribute, @Nullable String compositeDataKey, ObjectName objectName, boolean unsubscribeOnError) {
            this.metricName = metricName;
            this.labels = labels.immutableCopy();
            this.jmxAttribute = jmxAttribute;
            this.compositeDataKey = compositeDataKey;
            this.objectName = objectName;
            this.unsubscribeOnError = unsubscribeOnError;
        }


        void register(final MBeanServer server, final Tracer tracer) {
            logger.debug("Registering JMX metric {} {}.{} as metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            tracer.addGauge(metricName, labels, new DoubleSupplier() {
                @Override
                public double get() {
                    try {
                        double value;
                        if (compositeDataKey == null) {
                            value = ((Number) server.getAttribute(objectName, jmxAttribute)).doubleValue();
                        } else {
                            value = ((Number) ((CompositeData) server.getAttribute(objectName, jmxAttribute)).get(compositeDataKey)).doubleValue();
                        }
                        return value;
                    } catch (InstanceNotFoundException | AttributeNotFoundException | RuntimeMBeanException e) {
                        if (unsubscribeOnError) {
                            unregister(tracer);
                        }
                        return Double.NaN;
                    } catch (Exception e) {
                        return Double.NaN;
                    }
                }
            });
        }

        void unregister(Tracer tracer) {
            logger.debug("Unregistering JMX metric {} {}.{} metric_name: {} labels: {}", objectName, jmxAttribute, compositeDataKey, metricName, labels);
            tracer.removeGauge(metricName, labels);
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
        if (retryExecutor != null) {
            ExecutorUtils.shutdownAndWaitTermination(retryExecutor);
        }
    }
}
