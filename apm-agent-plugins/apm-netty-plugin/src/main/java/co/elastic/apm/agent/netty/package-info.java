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
@NonnullApi
/**
 *                                           {@link ChannelPipeline#write}        These methods are not good candidates for instrumentation because
 *                                           {@link ChannelPipeline#read()}       we'd not propagate context which is added within the handlers
 *                                           {@link ChannelPipeline#connect()}
 *                                           {@link ChannelPipeline#close()}
 *                                           {@link ChannelPipeline#disconnect()}
 *                           {@link ChannelPipeline}    |
 *  +---------------------------------------------------+----------------------+
 *  | {@link ChannelInboundHandler}          {@link ChannelOutboundHandler}    |
 *  |                                                   |                      |
 *  | +--------------------------------+     +----------+--------------------+ |
 *  | |{@link HttpClientCodec.Decoder} |     |{@link HttpClientCodec.Encoder}| | {@link HttpClientRequestDecoderInstrumentation}
 *  | +----------+---------------------+     +-------------------------------+ | {@link HttpClientRequestEncoderInstrumentation}
 *  +------------+--------------------------------------+----------------------+
 *              /|\                                     |
 *    {@link ChannelPipeline#fireChannelReadComplete}   |                        {@link ChannelReadCompleteContextRemovingInstrumentation}
 *    {@link ChannelPipeline#fireChannelRead}          \|/                       {@link ChannelReadContextRestoringInstrumentation}
 *  +------------+--------------------------------------+----------------------+
 *  |            |                                      |                      |
 *  |            |                            {@link Unsafe#write} ]           | {@link ChannelWriteContextStoringInstrumentation}
 *  |            |                            {@link Unsafe#beginRead()} ]     | {@link ChannelBeginReadContextStoringInstrumentation}
 *  |   {@link NioUnsafe#read()}                                               |
 *  |                                         {@link Unsafe#connect}]          | {@link ChannelConnectInstrumentation.ConnectContextStoringInstrumentation}
 *  |   {@link NioUnsafe#finishConnect()}                                      | {@link ChannelConnectInstrumentation.FinishConnectContextRestoringInstrumentation}
 *  |                                         {@link Unsafe#close()}]          | {@link ChannelCloseContextRemovingInstrumentation}
 *  |                                         {@link Unsafe#disconnect()}]     | {@link ChannelDisconnectContextRemovingInstrumentation}
 *  +--------------------------------------------------------------------------+
 *
 *  TODO
 *   - test promise failure (for example when can't connect)
 *   - instrument {@link ChannelPipeline#fireChannelInactive()}?
 *
 *
 * Example of a typical sequence of events for an HTTP client
 * <pre>
 * {@link TraceContextHolder#createSpan()}
 * {@link NioUnsafe#connect}
 *     instrumentation:
 *         {@link ChannelConnectInstrumentation.ConnectContextStoringInstrumentation}
 *     before:
 *         {@link NettyContextUtil#storeContext}
 *     error:
 *         {@link NettyContextUtil#removeContext}
 * {@link NioUnsafe#finishConnect()}
 *     instrumentation:
 *         {@link ChannelConnectInstrumentation.FinishConnectContextRestoringInstrumentation}
 *     before:
 *         {@link NettyContextUtil#restoreContext}
 *         {@link NettyContextUtil#removeContext}
 *     nested:
 *       - {@link Unsafe#write}
 *             instrumentation:
 *                 {@link ChannelWriteContextStoringInstrumentation}
 *             before:
 *                 {@link NettyContextUtil#storeContext}
 *       - {@link Unsafe#beginRead}
 *            instrumentation:
 *                {@link ChannelBeginReadContextStoringInstrumentation}
 *            before:
 *                 {@link NettyContextUtil#storeContext}
 *     after:
 *         {@link TraceContextHolder#deactivate()}
 * loop until all chunks of the response have been read:
 *     {@link ChannelPipeline#fireChannelRead}
 *         instrumentation:
 *             {@link ChannelReadContextRestoringInstrumentation}
 *         before:
 *             {@link NettyContextUtil#restoreContext}
 *         nested:
 *             # The HTTP client instrumentation is responsible for this:
 *             # depending on what part of the response is read, update or end the span
 *           - {@link Http#withStatusCode}
 *             # if the last chunk has been read
 *           - {@link Span#end()}
 *         after:
 *             {@link TraceContextHolder#deactivate()}
 *     {@link ChannelPipeline#fireChannelReadComplete}
 *         instrumentation:
 *             {@link ChannelReadCompleteContextRemovingInstrumentation}
 *         before:
 *             {@link NettyContextUtil#restoreContext}
 *             {@link NettyContextUtil#removeContext}
 *         nested:
 *             # triggers another {@link ChannelPipeline#fireChannelRead} when data arrives
 *           - {@link Unsafe#beginRead}
 *                instrumentation:
 *                    {@link ChannelBeginReadContextStoringInstrumentation}
 *                before:
 *                     {@link NettyContextUtil#storeContext}
 *         after:
 *             {@link TraceContextHolder#deactivate()}
 * {@link Unsafe#close()}
 *     instrumentation:
 *         {@link ChannelCloseContextRemovingInstrumentation}
 *     before:
 *         {@link NettyContextUtil#removeContext}
 * </pre>
 */
package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.annotation.NonnullApi;
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import io.netty.channel.Channel.Unsafe;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.nio.AbstractNioChannel.NioUnsafe;
import io.netty.handler.codec.http.HttpClientCodec;
