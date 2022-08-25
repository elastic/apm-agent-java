/*
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
 */
package co.elastic.apm.agent.mongodb.v4;

import co.elastic.apm.agent.mongodb.AbstractMongoClientInstrumentationIT;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class Mongo4LegacyIT extends AbstractMongoClientInstrumentationIT {

    private static MongoClient client;
    private static DB db;

    @BeforeClass
    public static void before() {
        client = new MongoClient( container.getContainerIpAddress(), container.getMappedPort(27017));
        db = client.getDB(DB_NAME);
    }

    @AfterClass
    public static void after() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    protected long update(Document query, Document updatedObject) throws Exception {
        return db.getCollection(COLLECTION_NAME).update(new BasicDBObject(query), new BasicDBObject(updatedObject)).getN();
    }

    @Override
    protected void dropCollection() throws Exception {
        db.getCollection(COLLECTION_NAME).drop();
    }

    @Override
    protected void delete(Document searchQuery) throws Exception {
        db.getCollection(COLLECTION_NAME).remove(new BasicDBObject(searchQuery));
    }

    @Override
    protected long collectionCount() throws Exception {
        return db.getCollection(COLLECTION_NAME).count();
    }

    @Override
    protected Collection<Document> find(Document query, int batchSize) throws Exception {
        try (DBCursor cursor = db.getCollection(COLLECTION_NAME).find(new BasicDBObject(query)).batchSize(batchSize)) {
            List<Document> result = new ArrayList<>();
            for (DBObject dbObject : cursor) {
                Map<String, Object> map = dbObject.toMap();
                map.remove("_id"); // remove ID field as it's included in serialized form
                result.add(new Document(map));
            }
            return result;
        }
    }

    @Override
    protected void insert(Document document) throws Exception {
        db.getCollection(COLLECTION_NAME).insert(new BasicDBObject(document));
    }

    @Override
    protected void insert(Document... document) throws Exception {
        List<BasicDBObject> objects = Arrays.stream(document).map(BasicDBObject::new).collect(Collectors.toList());
        db.getCollection(COLLECTION_NAME).insert(objects);
    }

    @Override
    protected void createCollection() throws Exception {
        db.createCollection(COLLECTION_NAME, new BasicDBObject());
    }

    @Override
    protected void listCollections() throws Exception {
        Iterator<String> names = db.getCollectionNames().iterator();
        if (names.hasNext()) {
            names.next();
        }
    }
}
