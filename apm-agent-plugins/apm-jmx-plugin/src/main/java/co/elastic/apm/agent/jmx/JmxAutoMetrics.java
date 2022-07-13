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

import co.elastic.apm.agent.metrics.DoubleSupplier;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.relation.MBeanServerNotificationFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JmxAutoMetrics {

    private final MetricRegistry registry;
    private final MBeanServer mbs;
    private final NotificationListener listener;

    private final List<Registration> registrations = new ArrayList<>();

    public JmxAutoMetrics(MetricRegistry registry, final MBeanServer mbs) {
        this.registry = registry;
        this.mbs = mbs;
        this.listener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                if (!(notification instanceof MBeanServerNotification)) {
                    return;
                }

                MBeanServerNotification mBeanServerNotification = ((MBeanServerNotification) notification);
                if (!mBeanServerNotification.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
                    return;
                }

                ObjectName newObjectName = mBeanServerNotification.getMBeanName();

                for (Registration registrations : registrations) {
                    registrations.newMbeanNotification(mbs, newObjectName);
                }
            }
        };
    }

    public void registerAll() {

        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        filter.enableAllObjectNames();
        filter.enableType(MBeanServerNotification.REGISTRATION_NOTIFICATION);

        try {
            mbs.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
        } catch (InstanceNotFoundException e) {
            throw new IllegalStateException(e);
        }

        // Tomcat
        // thread pools, Catalina:type=ThreadPool,name=*
        tomcatThreadPool(registry, mbs, "connectionCount", "tomcat.threadpool.connection.used"); // TODO rename to 'usage'
        tomcatThreadPool(registry, mbs, "maxConnections", "tomcat.threadpool.connection.max"); // TODO rename to 'limit'
        tomcatThreadPool(registry, mbs, "currentThreadCount", "tomcat.threads.count"); // TODO rename to 'usage' + breakdown 'idle' by difference
        tomcatThreadPool(registry, mbs, "currentThreadsBusy", "tomcat.threads.busy"); // TODO rename to 'usage' + breakdown 'busy'
        tomcatThreadPool(registry, mbs, "maxThreads", "tomcat.threads.max"); // TODO : rename to 'limit'
        // datasources Catalina:type=DataSource,host=localhost,context=*,class=javax.sql.DataSource,name=*
        tomcatDatasource(registry, mbs, "numActive", "tomcat.datasource.used"); // TODO : rename to usage + breakdown 'active'
        tomcatDatasource(registry, mbs, "numIdle", "tomcat.datasource.idle"); // TODO : rename to usage + breakdown 'idle'
        tomcatDatasource(registry, mbs, "maxTotal", "tomcat.datasource.max"); // TODO : rename to 'limit'
        // sessions Catalina:type=Manager,host=*,context=*
        tomcatSessions(registry, mbs, "activeSessions", "tomcat.sessions.used"); // TODO : rename to 'usage'
        tomcatSessions(registry, mbs, "maxActiveSessions", "tomcat.sessions.max"); // TODO : rename to 'limit'
        // transferred bytes Catalina:type=RequestProcessor,worker=*,name=*
        tomcatBytesTransferred(registry, mbs, "bytesReceived", "tomcat.bytes.received"); // TODO check OTel conventions here
        tomcatBytesTransferred(registry, mbs, "bytesSent", "tomcat.bytes.sent");

        for (Registration reg : registrations) {
            reg.initialSearch(mbs);
        }

    }

    private void tomcatBytesTransferred(final MetricRegistry registry, final MBeanServer mbs, final String attribute, final String metric) {
        register("Catalina:type=RequestProcessor,worker=*,name=*", attribute, new RegisterCallback() {
            @Override
            public void onRegister(ObjectName objectName, String attributeName) {
                registry.add(
                    metric,
                    Labels.Mutable.of("name", objectName.getKeyProperty("name"))
                        .add("worker", objectName.getKeyProperty("worker")),
                    attributeSupplier(mbs, objectName, attributeName));
            }
        });
    }

    private void tomcatSessions(final MetricRegistry registry, final MBeanServer mbs, final String attribute, final String metric) {
        register("Catalina:type=Manager,host=*,context=*", attribute, new RegisterCallback() {
            @Override
            public void onRegister(ObjectName objectName, String attributeName) {
                registry.add(
                    metric,
                    Labels.Mutable.of("host", objectName.getKeyProperty("host"))
                        .add("context", objectName.getKeyProperty("context")),
                    attributeSupplier(mbs, objectName, attributeName));
            }
        });
    }


    private void tomcatThreadPool(final MetricRegistry registry, final MBeanServer mbs, final String attribute, final String metric) {
        register("Catalina:type=ThreadPool,name=*", attribute, new RegisterCallback() {
            @Override
            public void onRegister(ObjectName objectName, String attributeName) {
                registry.add(
                    metric,
                    Labels.Mutable.of("name", normalizeTomcatName(objectName)),
                    attributeSupplier(mbs, objectName, attributeName));
            }
        });
    }

    private void tomcatDatasource(final MetricRegistry registry, final MBeanServer mbs, final String attribute, final String metric) {
        register("Catalina:type=DataSource,host=localhost,context=*,class=javax.sql.DataSource,name=*", attribute, new RegisterCallback() {
            @Override
            public void onRegister(ObjectName objectName, String attributeName) {
                registry.add(
                    metric,
                    // 'context' is ignored as the datasource is shared, we rely on metric set for de-duplication
                    Labels.Mutable.of("name", normalizeTomcatName(objectName)),
                    attributeSupplier(mbs, objectName, attributeName)
                );
            }
        });
    }

    private String normalizeTomcatName(ObjectName objectName) {
        String value = objectName.getKeyProperty("name");
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private DoubleSupplier attributeSupplier(final MBeanServer mbs, final ObjectName objectName, final String attribute) {
        return new DoubleSupplier() {
            @Override
            public double get() {
                Object attr = getAttribute(mbs, objectName, attribute);
                if (attr instanceof Number) {
                    double value = ((Number) attr).doubleValue();
                    // We have to filter when capturing the metric value and not only at registration because it is
                    // common to have a metrics, for example with pool limits that have no value on startup with a value of -1
                    // but they can still change at runtime.
                    if (value < 0) {
                        value = Double.NaN;
                    }
                    return value;
                } else {
                    throw new IllegalArgumentException(String.format("unexpected attribute type for %s %s", objectName, attribute));
                }
            }
        };
    }

    private interface RegisterCallback {
        void onRegister(ObjectName objectName, String attributeName);
    }

    private static abstract class Registration {
        protected ObjectName objectName;
        protected String attribute;

        private static class ForSimpleAttribute extends Registration {
            private final RegisterCallback cb;

            public ForSimpleAttribute(ObjectName objectName, String attribute, RegisterCallback cb) {
                super(objectName, attribute);
                this.cb = cb;
            }

            protected void executeCallback(ObjectName matchingObjectName) {
                cb.onRegister(matchingObjectName, attribute);
            }
        }

        public static Registration forSimpleAttribute(ObjectName objectName, String attribute, RegisterCallback cb) {
            return new ForSimpleAttribute(objectName, attribute, cb);
        }

        protected Registration(ObjectName objectName, String attribute) {
            this.objectName = objectName;
            this.attribute = attribute;
        }

        protected abstract void executeCallback(ObjectName matchingObjectName);

        public void newMbeanNotification(MBeanServer mbs, ObjectName newObjectName) {
            if (objectName.apply(newObjectName) && mbs.queryMBeans(newObjectName, null).size() > 0) {
                executeCallback(newObjectName);
            }
        }

        public void initialSearch(MBeanServer mbs) {
            Set<ObjectInstance> instances = mbs.queryMBeans(objectName, null);
            for (ObjectInstance instance : instances) {
                executeCallback(instance.getObjectName());
            }
        }
    }

    private void register(String objectName, String attribute, RegisterCallback cb) {
        registrations.add(Registration.forSimpleAttribute(getObjectName(objectName), attribute, cb));
    }

    private static ObjectName getObjectName(String s) {
        try {
            return new ObjectName(s);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Nullable
    private static Object getAttribute(MBeanServer mbs, ObjectName objectName, String attribute) {
        try {
            return mbs.getAttribute(objectName, attribute);
        } catch (InstanceNotFoundException | AttributeNotFoundException e) {
            // ignore if absent
            return null;
        } catch (JMException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
