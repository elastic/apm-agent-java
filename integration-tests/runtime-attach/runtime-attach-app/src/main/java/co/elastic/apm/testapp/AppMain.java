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
package co.elastic.apm.testapp;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import co.elastic.apm.attach.ElasticApmAttacher;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AppMain implements AppJmx {

    private static final int WORK_CYCLE_DURATION_MS = 100;
    private static final int MAX_CYCLE_COUNT = 1000;

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        AppMain app = new AppMain();

        int maxArg = MAX_CYCLE_COUNT;
        if (args.length > 0) {
            try {
                maxArg = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        boolean selfAttach = false;
        if (args.length > 1) {
            selfAttach = args[1].equals("self-attach");
        }

        registerJmx(app);

        if (selfAttach) {
            System.out.println("Using self-attach");
            ElasticApmAttacher.attach();
        }

        System.out.println("application start, timeout = " + maxArg);
        app.start();


        int left = maxArg;
        while (!app.doExit.get() && left-- > 0) {
            try {
                Thread.sleep(WORK_CYCLE_DURATION_MS);

            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        long endTime = System.currentTimeMillis();
        if (left == 0) {
            throw new IllegalStateException("timeout");
        } else {
            System.out.format("application exit, total time = %d ms", endTime - startTime);
        }


    }

    private static void registerJmx(AppJmx mxBean) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName objectName = new ObjectName("co.elastic.apm.testapp:type=AppMXBean");
            mbs.registerMBean(mxBean, objectName);
            System.out.println("application JMX registration OK " + objectName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final AtomicBoolean doExit;
    private final AtomicInteger workCount;
    private final AtomicInteger workInstrumentedCount;

    private final ScheduledExecutorService executor;

    private AppMain() {
        executor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
        doExit = new AtomicBoolean();
        workCount = new AtomicInteger();
        workInstrumentedCount = new AtomicInteger();
    }

    public void start() {
        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Transaction transaction = ElasticApm.startTransaction();
                String id = transaction.getId();
                transaction.setName("work task");
                try {
                    Thread.sleep(Math.max(10, WORK_CYCLE_DURATION_MS / 10));
                } catch (InterruptedException ignored) {
                }
                transaction.end();

                workCount.incrementAndGet();
                if (!id.isEmpty()) {
                    workInstrumentedCount.incrementAndGet();
                }
            }
        }, 0, WORK_CYCLE_DURATION_MS, TimeUnit.MILLISECONDS);
    }


    @Override
    public int getWorkUnitsCount() {
        return workCount.get();
    }

    @Override
    public int getInstrumentedWorkUnitsCount() {
        return workInstrumentedCount.get();
    }

    @Override
    public void exit() {
        executor.shutdown();
        doExit.set(true);
    }

}
