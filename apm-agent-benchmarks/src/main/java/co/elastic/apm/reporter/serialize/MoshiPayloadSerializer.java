package co.elastic.apm.reporter.serialize;

import co.elastic.apm.intake.transactions.Payload;
import co.elastic.apm.report.serialize.PayloadSerializer;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Rfc3339DateJsonAdapter;
import okio.BufferedSink;

import java.io.IOException;
import java.util.Date;

public class MoshiPayloadSerializer implements PayloadSerializer {

    private final Moshi moshi;
    private final JsonAdapter<Payload> jsonAdapter;

    public MoshiPayloadSerializer() {
        moshi = new Moshi.Builder()
            .add(Date.class, new Rfc3339DateJsonAdapter())
            .build();
        jsonAdapter = moshi.adapter(Payload.class);
    }

    public void serializePayload(BufferedSink sink, Payload payload) throws IOException {
        jsonAdapter.toJson(sink, payload);
    }

}
