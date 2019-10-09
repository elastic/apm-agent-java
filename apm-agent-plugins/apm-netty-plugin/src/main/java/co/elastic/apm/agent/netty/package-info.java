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
 *                                           {@link ChannelPipeline#connect}      we'd not propagate context which is added within the handlers
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
 */
package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.annotation.NonnullApi;
import io.netty.channel.Channel.Unsafe;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.nio.AbstractNioChannel.NioUnsafe;
import io.netty.handler.codec.http.HttpClientCodec;
