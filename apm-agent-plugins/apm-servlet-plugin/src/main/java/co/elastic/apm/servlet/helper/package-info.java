/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
/**
 * <p>
 * The classes in this package are loaded by a class loader which is a child of the class loader the Servlet API is loaded from.
 * This works around the problem that the agent does not have access to the Servlet API.
 * Instead of injecting the helper classes to the same class loader the Servlet API is loaded from,
 * which will most likely not be possible anymore as of Java 11
 * (see http://jdk.java.net/11/release-notes#JDK-8193033),
 * we create a class loader as the child of the one which loads the Servlet API.
 * </p>
 * <p>
 * The classes loaded by this helper class loader can then access the Servlet API.
 * However, the agent itself can't directly access the classes from the helper class loader,
 * because they are loaded by a child class loader.
 * </p>
 * <p>
 * This problem is circumvented by the agent providing an interface,
 * which the helper class implements.
 * The agent does then not need to know about the implementation type of the interface.
 * </p>
 * <p>
 * One thing to be aware of is that the helper class loader needs to implement child first semantics when loading classes.
 * Otherwise, it would load the helper implementation from the system or bootstrap classloader (where the agent is loaded from),
 * without access to the Servlet API,
 * instead of loading them itself so that the helper classes can access the Servlet API.
 * </p>
 * <p>
 * Advices have to manually add their required helper classes to {@link co.elastic.apm.bci.HelperClassManager},
 * which takes care of creating the helper class loaders.
 * </p>
 *
 * <pre>
 *          System/Bootstrap CL (Agent)     provides interface StartAsyncAdviceHelper
 *                     |
 *                     v
 *             App CL (Servlet API)         uses StartAsyncAdviceHelperImpl from Helper CL
 *               /              \
 *              v               v
 * WebApp CL (user code/libs)   Helper CL   implements StartAsyncAdviceHelperImpl
 * </pre>
 */
@NonnullApi
package co.elastic.apm.servlet.helper;

import co.elastic.apm.annotation.NonnullApi;
