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
package co.elastic.apm.benchmark.serializer;

import co.elastic.apm.impl.payload.Payload;
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
