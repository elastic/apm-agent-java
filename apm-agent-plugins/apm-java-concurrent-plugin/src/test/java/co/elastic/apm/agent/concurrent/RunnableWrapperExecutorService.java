/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.impl.ElasticApmTracer;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

public class RunnableWrapperExecutorService extends ExecutorServiceWrapper {

    private final ElasticApmTracer tracer;

    public static RunnableWrapperExecutorService wrap(ExecutorService delegate, ElasticApmTracer tracer) {
        return new RunnableWrapperExecutorService(delegate, tracer);
    }

    private RunnableWrapperExecutorService(ExecutorService delegate, ElasticApmTracer tracer) {
        super(delegate);
        this.tracer = tracer;
    }

    @Override
    public void execute(@Nonnull Runnable command) {
        super.execute(() -> {
            assertThat(tracer.getActive()).isNull();
            command.run();
            assertThat(tracer.getActive()).isNull();
        });
    }

    @Override
    public Future<?> submit(@Nonnull Runnable task) {
        return super.submit(() -> {
            assertThat(tracer.getActive()).isNull();
            task.run();
            assertThat(tracer.getActive()).isNull();
        });
    }

    @Override
    public <T> Future<T> submit(@Nonnull Runnable task, T result) {
        return super.submit(() -> {
            assertThat(tracer.getActive()).isNull();
            task.run();
            assertThat(tracer.getActive()).isNull();
        }, result);
    }

    @Override
    public <T> Future<T> submit(@Nonnull Callable<T> task) {
        return super.submit(() -> {
            assertThat(tracer.getActive()).isNull();
            T ret = task.call();
            assertThat(tracer.getActive()).isNull();
            return ret;
        });
    }
}
