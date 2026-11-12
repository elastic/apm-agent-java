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
package co.elastic.apm.springai.testapp;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/mcp")
public class McpToolController {

    private final ChatModel chatModel;
    private final ToolCallback echoTool;
    private final ToolCallingManager toolCallingManager;

    public McpToolController(ChatModel chatModel, SyncMcpToolCallbackProvider mcpTools) {
        this.chatModel = chatModel;
        ToolCallback[] tracedMcpTools = Arrays.stream(mcpTools.getToolCallbacks())
            .map(McpTracingToolCallback::new)
            .toArray(ToolCallback[]::new);
        echoTool = Arrays.stream(tracedMcpTools)
            .filter(callback -> callback.getToolDefinition().name().endsWith("echo"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("MCP echo tool was not discovered"));
        if (chatModel instanceof TestDataConfiguration.DeterministicChatModel deterministicChatModel) {
            deterministicChatModel.setToolName(echoTool.getToolDefinition().name());
        }
        toolCallingManager = ToolCallingManager.builder().build();
    }

    @PostMapping(value = "/tools/echo", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public McpToolReply invokeEchoTool(@RequestBody String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tool arguments must not be blank");
        }
        return new McpToolReply(echoTool.getToolDefinition().name(), echoTool.call(arguments));
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public McpChatReply chatWithEchoTool(@RequestBody McpChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
            .model("tool-test")
            .toolCallbacks(echoTool)
            .build();
        Prompt initialPrompt = new Prompt(List.of(
            new SystemMessage("Call the echo tool exactly once and use its result in the answer."),
            new UserMessage("Call the echo tool with this exact message and report its result: "
                + request.message().trim())), options);
        ChatResponse toolCallResponse = chatModel.call(initialPrompt);
        ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(initialPrompt, toolCallResponse);
        ChatResponse finalResponse = chatModel.call(new Prompt(toolResult.conversationHistory(), options));
        return new McpChatReply(finalResponse.getResult().getOutput().getText());
    }

    public record McpChatRequest(String message) {
    }

    public record McpChatReply(String content) {
    }

    public record McpToolReply(String tool, String result) {
    }

    private static final class McpTracingToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        private McpTracingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(toolInput, null);
        }
    }
}
