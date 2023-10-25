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
package co.elastic.apm.agent.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class ProcessOtelBenchmarkResults {

    private final ArrayNode bechmarkResultJson;
    private final String resultFilePath;
    private final String elasticVersion;
    private final String otelVersion;
    private ObjectMapper objectMapper;

    private ProcessOtelBenchmarkResults(OtelBenchParser parser, String resultFilePath, String elasticVersion, String otelVersion) throws IOException {
        this.resultFilePath = resultFilePath;
        this.elasticVersion = elasticVersion;
        this.otelVersion = otelVersion;
        objectMapper = new ObjectMapper();
        bechmarkResultJson = parser.createTree(objectMapper);
    }

    /**
     * Example file contents for args 0:
     *
     * <sometag-eg-pre>
     * Performing startup warming phase for 60 seconds...
     * Starting disposable JFR warmup recording...
     * Stopping disposable JFR warmup recording...
     * Warmup complete.
     * ----------------------------------------------------------
     *  Run at Tue Oct 24 16:11:38 UTC 2023
     *  release : compares no agent, latest stable, and latest snapshot agents
     *  5 users, 5000 iterations
     * ----------------------------------------------------------
     * Agent               :              none           latest         snapshot   elastic-latest    elastic-async elastic-snapshot
     * Run duration        :          00:00:57         00:01:07         00:01:08         00:01:01         00:01:03         00:01:00
     * Avg. CPU (user) %   :         0.3620707        0.4131534       0.40864184       0.39786905       0.42508292        0.3923088
     * Max. CPU (user) %   :         0.5321782       0.57107234        0.5597015        0.5860349       0.58168316       0.54613465
     * Avg. mch tot cpu %  :        0.94261116       0.95221955       0.93572134        0.9339143        0.9495207       0.93751615
     * Startup time (ms)   :             11378            12870            12890            12861             8686            12868
     * Total allocated MB  :          15069.28         20168.60         20155.76         16018.64         15854.65         15904.63
     * Min heap used (MB)  :             74.39           123.06           115.00           139.10           114.14           108.85
     * Max heap used (MB)  :            430.09           414.73           391.81           543.44           474.40           387.21
     * Thread switch rate  :         54890.133        54588.465        54918.758        54230.484        53694.992        53908.035
     * GC time (ms)        :               417              606              669              920              406              520
     * GC pause time (ms)  :               423              606              669              920              406              520
     * Req. mean (ms)      :              4.21             4.97             5.01             4.51             4.67             4.40
     * Req. p95 (ms)       :             10.66            12.96            13.06            11.74            11.96            11.29
     * Iter. mean (ms)     :             55.19            64.99            65.60            58.89            61.06            57.74
     * Iter. p95 (ms)      :             85.51            99.71           100.96            89.89            95.43            88.39
     * Net read avg (bps)  :       13733443.00      12234844.00      12082882.00      12154885.00      10863340.00      11873964.00
     * Net write avg (bps) :       18365140.00      66959119.00      66142391.00      16239026.00      14507576.00      15864209.00
     * Peak threads        :                40               54               53               48               48               48
     * </sometag-eg-pre>
     *
     *
     * @param args 0 path to the test results (normally `build/reports/tests/test/classes/io.opentelemetry.OverheadTests.html`)
     * @param args 1 path to output file to be created by this class (eg `output.json`)
     * @param args 2 elastic version used in the test (eg `1.43.0`)
     * @param args 3 path to otel "latest" jar file used in the test (normally `opentelemetry-javaagent.jar`)
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        OtelBenchParser parser = new OtelBenchParser();
        try (BufferedReader rdr = new BufferedReader(new FileReader(args[0]))) {
            String line;
            while ((line = rdr.readLine()) != null) {
                parser.parse(line);
            }
        }
        new ProcessOtelBenchmarkResults(parser, args[1], args[2], getVersionFromJar(args[3])).process();
    }

    private void process() throws IOException {
        final JsonNode meta = bechmarkResultJson.objectNode()
            .put("os_name", System.getProperty("os.name"))
            .put("os_version", System.getProperty("os.version"))
            .put("jdk_version", System.getProperty("java.version"))
            .put("elastic_version", elasticVersion)
            .put("otel_version", otelVersion)
            .put("executed_at", Instant.now().toString());
        for (JsonNode benchmark : bechmarkResultJson) {
            ((ObjectNode) benchmark).put("@timestamp", System.currentTimeMillis());
            ((ObjectNode) benchmark).set("meta", meta);
        }
        writeBulkFile(this.resultFilePath);
    }

    private static String getVersionFromJar(String jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath)) {
            return jar.getManifest().getMainAttributes().getValue("Implementation-Version");
        }
    }

    private void writeBulkFile(String resultFilePath) throws IOException {
        final File file = new File(resultFilePath);
        final FileWriter fileWriter = new FileWriter(file);
        for (JsonNode benchmark : bechmarkResultJson) {
            fileWriter.append("{ \"index\" : { \"_index\" : \"microbenchmarks\" } }\n");
            fileWriter.append(objectMapper.writer().writeValueAsString(benchmark));
            fileWriter.append("\n");
        }
        fileWriter.close();
    }

    public static class OtelBenchParser {

        private boolean inResults;
        private String runAtComment;
        private String releaseComment;
        private String configComment;
        private String[] agentNames;
        private String[] runDurations;
        private List<ResultValues> results = new ArrayList<>();

        public ArrayNode createTree(ObjectMapper objectMapper) {
            ArrayNode rootNode = objectMapper.createArrayNode();
            for (int i = 0; i < agentNames.length; i++) {
                ObjectNode benchmarkNode = rootNode.objectNode();
                String agentName = agentNames[i].toLowerCase().contains("elastic") ? agentNames[i] : "otel-"+agentNames[i] ;
                if (agentNames[i].equals("none")) {
                    agentName = "none";
                }
                benchmarkNode.put("agentName", agentName);
                benchmarkNode.put("benchmark", "benchmarkJavaApmWithOtel."+agentName);
                for (ResultValues values: results) {
                    benchmarkNode.put(values.getJsonTitle(), values.get(i));
                }

                rootNode.add(benchmarkNode);
            }
            return rootNode;
        }

        public void parse(String result) throws ParseException {
            if (inResults) {
                if (runAtComment == null) {
                    runAtComment = result.strip();
                } else if (releaseComment == null) {
                    releaseComment = result.strip();
                } else  if (configComment == null) {
                    configComment = result.strip();
                } else if (result.matches("\\s*\\-+\\s*")) {
                    //skip
                } else if (agentNames == null) {
                    String[] agentAndNames = result.split(":");
                    if (agentAndNames[0].trim().equals("Agent")) {
                        agentNames = agentAndNames[1].trim().split("\\s+");
                    } else {
                        throw new ParseException("Expecting 'Agent               :              none           latest ....' but got: "+result);
                    }
                } else if (runDurations == null) {
                    String[] durations = result.split("\\s+:\\s+");
                    if (durations[0].trim().equals("Run duration")) {
                        runDurations = durations[1].trim().split("\\s+");
                    } else {
                        throw new ParseException("Expecting 'Run duration               :              nn:nn:nn           nn:nn:nn ....' but got: "+result);
                    }
                } else if (result.matches("\\s*\\<.*")) {
                    inResults = false;
                } else {
                    results.add(new ResultValues(result, agentNames.length));
                }
            } else if (result.matches("\\s*\\-+\\s*")) {
                inResults = true;
            } else {
                System.out.println("X "+result);
            }
        }

        public static class ResultValues {
            String valuesTitle;
            double[] values;
            public ResultValues(String resultLine, int expectedValuesCount) throws ParseException {
                String[] titleAndResults = resultLine.split(":");
                if (titleAndResults.length != 2) {
                    throw new ParseException("Expected '<title> : <value1 value2 ...>' but got: "+resultLine);
                }
                valuesTitle = titleAndResults[0].trim();
                String[] resultValues = titleAndResults[1].trim().split("\\s+");
                if (resultValues.length != expectedValuesCount) {
                    throw new ParseException("Expected "+expectedValuesCount+" values but got "+resultValues.length +" for line: "+resultLine);
                }
                values = new double[expectedValuesCount];
                for (int i = 0; i < resultValues.length; i++) {
                    try {
                        values[i] = Double.parseDouble(resultValues[i]);
                    } catch (NumberFormatException e) {
                        throw new ParseException("Expected but didn't get numeric float value at result index "+i+" in results: "+resultLine);
                    }
                }
            }

            public String getJsonTitle() {
                return valuesTitle.toLowerCase().replaceAll("[^\\w ]","").trim().replace(' ','_');
            }

            public double get(int i) {
                return values[i];
            }
        }

        public static class ParseException extends Exception {
            public ParseException(String string) {
                super(string);
            }

        }
    }
}
