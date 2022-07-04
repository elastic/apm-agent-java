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
package co.elastic.apm.agent.mongoclient;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class MongoClientSyncInstrumentation2IT extends AbstractMongoClientInstrumentationTest { // TODO : maybe rename this

    private static MongoClient mongo = null;
    private static MongoDatabase db = null;

    @BeforeClass
    public static void startMongoContainerAndClient() {
        mongo = MongoClients.create(String.format("mongodb://%s:%d", container.getContainerIpAddress(), container.getMappedPort(27017)));
        db = mongo.getDatabase(DB_NAME);
    }

    @AfterClass
    public static void closeClient() {
        if(mongo != null) {
            mongo.close();
        }
        mongo = null;
        db = null;
    }

    @Override
    public void createCollection() {
        db.createCollection(COLLECTION_NAME);
    }

    @Override
    public void dropCollection() {
        db.getCollection(COLLECTION_NAME).drop();
    }

    @Override
    public long count() {
        return db.getCollection(COLLECTION_NAME).count();
    }

    @Override
    public long update(Document query, Document updatedObject) {
        return db.getCollection(COLLECTION_NAME).updateOne(query, updatedObject).getModifiedCount();
    }

    @Override
    public Collection<Document> find(Document query, int batchSize) {
        return db.getCollection(COLLECTION_NAME).find(query).batchSize(batchSize).into(new ArrayList<>());
    }

    @Override
    public void insert(Document document) {
        db.getCollection(COLLECTION_NAME).insertOne(document);
    }

    @Override
    protected void insert(Document... document) {
        db.getCollection(COLLECTION_NAME).insertMany(Arrays.asList(document));
    }

    @Override
    public void delete(Document searchQuery) {
        db.getCollection(COLLECTION_NAME).deleteOne(searchQuery);
    }

    @Override
    protected void listCollections() {
        db.listCollections().first();
    }
}
