package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

public class AlibabaCallbackHolder {
    public static final WeakConcurrentMap<ResponseCallback, AbstractSpan<?>> callbackSpanMap = WeakMapSupplier.createMap();
}
