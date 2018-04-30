/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

public class PostProcessBenchmarkResults {

    private final ArrayNode jmhResultJson;
    private final String resultFilePath;
    private final long timestamp;
    private ObjectMapper objectMapper;

    private PostProcessBenchmarkResults(String jmhResultJsonPath, String resultFilePath, long timestamp) throws IOException {
        this.resultFilePath = resultFilePath;
        this.timestamp = timestamp;
        objectMapper = new ObjectMapper();
        jmhResultJson = (ArrayNode) objectMapper.readTree(new File(jmhResultJsonPath));
    }

    public static void main(String[] args) throws Exception {
        final long timestamp;
        if (args.length > 2) {
            timestamp = Long.parseLong(args[2]);
        } else {
            timestamp = System.currentTimeMillis();
        }
        new PostProcessBenchmarkResults(args[0], args[1], timestamp).process();
    }

    private static String execCmd(String cmd) {
        try {
            java.util.Scanner s = new java.util.Scanner(Runtime.getRuntime().exec(cmd).getInputStream()).useDelimiter("\n");
            return s.hasNext() ? s.next() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void process() throws IOException {
        final JsonNode meta = jmhResultJson.objectNode()
            .put("cpu_model", execCmd("lscpu | grep \"Model name\" | awk '{for(i=3;i<=NF;i++){printf \"%s \", $i}; printf \"\\n\"}'"))
            .put("os_name", System.getProperty("os.name"))
            .put("os_version", System.getProperty("os.version"))
            .put("jdk_version", System.getProperty("java.version"))
            .put("revision", execCmd(String.format("git -C %s rev-parse --short HEAD", System.getenv("WORKSPACE"))))
            .put("commit_message", execCmd(String.format("git -C %s log --format=%%s -n 1 HEAD", System.getenv("WORKSPACE"))))
            .put("executed_at", Instant.now().toString());
        for (JsonNode benchmark : jmhResultJson) {
            final String benchmarkName = benchmark.get("benchmark").textValue();
            ((ObjectNode) benchmark).put("benchmark", benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1));
            ((ObjectNode) benchmark).put("@timestamp", timestamp);
            ((ObjectNode) benchmark).set("meta", meta);

            final ObjectNode primaryMetric = (ObjectNode) benchmark.get("primaryMetric");
            removeFields(primaryMetric, fieldName -> fieldName.startsWith("raw"));
            removePercentileSecondaryMetrics((ObjectNode) benchmark.get("secondaryMetrics"));
            removeFields((ObjectNode) benchmark.get("secondaryMetrics"), fieldName -> fieldName.startsWith("gc.churn"));
            for (JsonNode secondaryMetric : benchmark.get("secondaryMetrics")) {
                removeFields((ObjectNode) secondaryMetric, fieldName -> fieldName.startsWith("raw"));
            }
        }
        jmhResultJson.add(subtractBenchmarkResults(getBenchmarkByName("benchmarkWithApm"), getBenchmarkByName("benchmarkWithoutApm")));
        writeBulkFile(this.resultFilePath);
    }


    private void writeBulkFile(String resultFilePath) throws IOException {
        final File file = new File(resultFilePath);
        final FileWriter fileWriter = new FileWriter(file);
        for (JsonNode benchmark : jmhResultJson) {
            fileWriter.append("{ \"index\" : { \"_index\" : \"microbenchmarks\", \"_type\" : \"_doc\" } }\n");
            fileWriter.append(objectMapper.writer().writeValueAsString(benchmark));
            fileWriter.append("\n");
        }
        fileWriter.close();
    }

    private void removeFields(ObjectNode node, Predicate<String> fieldNamePredicate) {
        List<String> fieldNamesToRemove = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            final String fieldName = it.next();
            if (fieldNamePredicate.test(fieldName)) {
                fieldNamesToRemove.add(fieldName);
            }
        }
        node.remove(fieldNamesToRemove);
    }

    private void removePercentileSecondaryMetrics(ObjectNode secondaryMetrics) {
        removeFields(secondaryMetrics, fieldName -> fieldName.contains("p1.") || fieldName.contains("p0."));
    }

    private ObjectNode subtractBenchmarkResults(ObjectNode benchmark, ObjectNode benchmarkBaseline) {
        final ObjectNode result = benchmark.deepCopy();
        result.put("benchmark", result.get("benchmark").textValue() + ".delta");
        subtract((ObjectNode) result.get("primaryMetric"), (ObjectNode) benchmarkBaseline.get("primaryMetric"));
        subtract((ObjectNode) result.get("secondaryMetrics"), (ObjectNode) benchmarkBaseline.get("secondaryMetrics"));
        return result;
    }

    private void subtract(ObjectNode benchmark1, ObjectNode benchmark2) {
        for (Iterator<String> it = benchmark1.fieldNames(); it.hasNext(); ) {
            final String fieldName = it.next();
            final JsonNode jsonNode1 = benchmark1.get(fieldName);
            final JsonNode jsonNode2 = benchmark2.get(fieldName);
            if (jsonNode1.isObject() && jsonNode2 != null && jsonNode2.isObject()) {
                subtract((ObjectNode) jsonNode1, (ObjectNode) jsonNode2);
            } else if (jsonNode1.isNumber() && jsonNode2 != null) {
                benchmark1.put(fieldName, jsonNode1.decimalValue().subtract(jsonNode2.decimalValue()).doubleValue());
            }
        }
    }

    private ObjectNode getBenchmarkByName(String benchmarkName) {
        for (JsonNode benchmark : jmhResultJson) {
            if (benchmark.get("benchmark").textValue().equals(benchmarkName)) {
                return (ObjectNode) benchmark;
            }
        }
        return null;
    }
}
