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
package co.elastic.apm.agent.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

public class TestUtils {

    private final static ObjectMapper objectMapper = new ObjectMapper(); ;

    public static ArrayList<JsonNode> readJsonFile(String jsonFilePath) throws IOException {
        ArrayList<JsonNode> jsonFileLines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(Paths.get(jsonFilePath))) {
            stream.forEach(line -> {
                try {
                    jsonFileLines.add(objectMapper.readTree(line));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            });
        }
        return jsonFileLines;
    }
}
