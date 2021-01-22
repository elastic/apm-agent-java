package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import javax.annotation.Nullable;

class AttributeMapper {

    static void mapAttribute(AbstractSpan<?> span, AttributeKey<?> key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (span.isSampled()) {
            // TODO translate other well-known attributes
            if (key.equals(SemanticAttributes.HTTP_STATUS_CODE)) {
                int statusCode = ((Number) value).intValue();
                if (span instanceof Transaction) {
                    ((Transaction) span).getContext().getResponse().withStatusCode(statusCode);
                    ((Transaction) span).withResult(ResultUtil.getResultByHttpStatus(statusCode));
                } else if (span instanceof Span) {
                    ((Span) span).getContext().getHttp().withStatusCode(statusCode);
                }
            }
            setUnknownAttributeAsLabel(span, key, value);
        }
    }

    private static void setUnknownAttributeAsLabel(AbstractSpan<?> span, AttributeKey<?> key, Object value) {
        if (value instanceof Boolean) {
            span.addLabel(key.getKey(), (Boolean) value);
        } else if (value instanceof Number) {
            span.addLabel(key.getKey(), (Number) value);
        } else {
            span.addLabel(key.getKey(), value.toString());
        }
    }
}
