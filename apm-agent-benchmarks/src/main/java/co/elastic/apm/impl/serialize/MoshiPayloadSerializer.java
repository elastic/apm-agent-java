package co.elastic.apm.impl.serialize;

import co.elastic.apm.impl.TransactionPayload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Rfc3339DateJsonAdapter;
import okio.BufferedSink;

import java.io.IOException;
import java.util.Date;

public class MoshiPayloadSerializer implements PayloadSerializer {

    private final Moshi moshi;
    private final JsonAdapter<TransactionPayload> jsonAdapter;

    public MoshiPayloadSerializer() {
        moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();
        jsonAdapter = moshi.adapter(TransactionPayload.class);
    }

    public void serializePayload(BufferedSink sink, TransactionPayload payload) throws IOException {
        jsonAdapter.toJson(sink, payload);
    }

}
