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
package co.elastic.apm.agent.quartz.job;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class JobTransactionNameInstrumentation extends ElasticApmInstrumentation {
	public static final String TRANSACTION_TYPE = "quartz";
    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(JobTransactionNameInstrumentation.class);

    private final Collection<String> applicationPackages;

    public JobTransactionNameInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void setTransactionName(@Advice.Argument(readOnly = true, value = 0) Object context,
    		@SimpleMethodSignature String signature, @Advice.Origin Class<?> clazz, @Advice.Local("transaction") Transaction transaction) 
    		throws InvocationTargetException, NoSuchMethodException {
        if (tracer != null) {
            TraceContextHolder<?> active = tracer.getActive();
            if(context == null) {
            	logger.warn("Cannot correctly name transaction for method {} because JobExecutionContext is null", signature);
            	transaction = tracer.startTransaction(TraceContext.asRoot(), null, clazz.getClassLoader())
	                    .withName(signature)
	                    .withType(TRANSACTION_TYPE)
	                    .activate();
            }else if (active == null) {
            	String transactionName;
            	Class<?> contextClass=context.getClass();
            	boolean success = false;
				try {
					Object jobDetail = contextClass.getMethod("getJobDetail").invoke(context);
	            	Object jobKey=jobDetail.getClass().getMethod("getKey").invoke(jobDetail);
	            	transactionName = jobKey.toString();
	            	success = true;
	                
				} catch (Exception e) {
					logger.error(
							String.format("Cannot correctly name transaction for method %s because context.getJobDetail().getKey() failed", 
									signature), 
							e);
					transactionName = signature;
				}
				transaction = tracer.startTransaction(TraceContext.asRoot(), null, clazz.getClassLoader())
	                    .withName(transactionName)
	                    .withType(TRANSACTION_TYPE)
	                    .activate();
				if(success) {
					Object trigger;
					try {
						trigger = contextClass.getMethod("getTrigger").invoke(context);
		            	Object triggerKey=trigger.getClass().getMethod("getKey").invoke(trigger);
						transaction.addCustomContext("trigger", triggerKey.toString());
						transaction.addCustomContext("scheduledTime", contextClass.getMethod("getScheduledFireTime")
								.invoke(context).toString());
						if(transaction.getResult()==null) {
							Object quartzResult=contextClass.getMethod("getResult").invoke(context);
							if(quartzResult!=null) {
								transaction.withResult(quartzResult.toString());
							}
						}
					} catch (Exception e) {
						logger.warn("Failed to add custom context because of reflection errors", e);
					}
				}
            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("transaction") Transaction transaction,
                                    @Advice.Thrown Throwable t) {
        if (transaction != null) {
            transaction.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>none())
        	.and(hasSuperType(named("org.quartz.Job")))
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").and(takesArgument(0, named("org.quartz.JobExecutionContext")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(TRANSACTION_TYPE);
    }
}
