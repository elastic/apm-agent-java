package co.elastic.apm.agent.netty;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Span;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link HttpClientCodec.Decoder#decode(io.netty.channel.ChannelHandlerContext, io.netty.buffer.ByteBuf, java.util.List)}
 *
 * Can be executed multiple times for the same HTTP response, for example in case of chunked responses.
 */
public class HttpClientRequestDecoderInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.netty.handler.codec.http.HttpObjectDecoder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("decode")
            .and(takesArguments(3))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, named("io.netty.buffer.ByteBuf")))
            .and(takesArgument(2, named("java.util.List")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("netty");
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    private static void afterDecode(@Advice.Argument(0) ChannelHandlerContext ctx,
                                    @Advice.Argument(2) List<Object> out) {
        System.out.println("HttpObjectDecoder#decode");
        Attribute<Span> spanAttr = ctx.channel().attr(AttributeKey.<Span>valueOf("elastic.apm.trace_context.client"));
        for (int i = 0, size = out.size(); i < size; i++) {
            Object msg = out.get(i);
            Span httpSpan = spanAttr.get();
            if (httpSpan != null) {
                if (msg instanceof HttpResponse) {
                    httpSpan.getContext().getHttp().withStatusCode(((HttpResponse) msg).status().code());
                }
                if (msg instanceof LastHttpContent) {
                    spanAttr.set(null);
                    httpSpan.end();
                    NettyContextUtil.removeContext(ctx.channel());
                }
            }
        }
    }
}
