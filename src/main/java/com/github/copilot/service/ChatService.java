package com.github.copilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.client.CopilotClient;
import com.github.copilot.config.CopilotConfig;
import com.github.copilot.model.OpenAIModels;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for Copilot Chat Completions
 */
@ApplicationScoped
public class ChatService {

    @Inject
    CopilotClient copilotClient;

    @Inject
    AuthService authService;

    @Inject
    CopilotConfig config;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Non-streaming chat completion
     */
    public OpenAIModels.ChatCompletionResponse chatCompletion(OpenAIModels.ChatCompletionRequest request) {
        try {
            String copilotToken = authService.getCopilotToken();
            
            // Apply default model if not specified
            if (request.model() == null || request.model().isEmpty()) {
                request = new OpenAIModels.ChatCompletionRequest(
                    config.defaultModel(),
                    request.messages(),
                    request.maxTokens(),
                    request.temperature(),
                    request.topP(),
                    request.stream(),
                    request.stop(),
                    request.tools(),
                    request.toolChoice(),
                    request.frequencyPenalty(),
                    request.presencePenalty()
                );
            }
            
            return copilotClient.chatCompletion(copilotToken, request);
        } catch (Exception e) {
            Log.errorf(e, "Chat completion failed");
            throw new RuntimeException("Chat completion failed", e);
        }
    }

    /**
     * Streaming chat completion - returns Multi of chunks
     */
    public Multi<OpenAIModels.ChatCompletionChunk> chatCompletionStream(OpenAIModels.ChatCompletionRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            try {
                String copilotToken = authService.getCopilotToken();
                
                copilotClient.chatCompletionStream(copilotToken, request, data -> {
                    if ("[DONE]".equals(data)) {
                        emitter.complete();
                    } else {
                        try {
                            var chunk = objectMapper.readValue(data, OpenAIModels.ChatCompletionChunk.class);
                            emitter.emit(chunk);
                        } catch (JsonProcessingException e) {
                            Log.warnf("Failed to parse chunk: %s", data);
                        }
                    }
                });
            } catch (Exception e) {
                Log.errorf(e, "Streaming chat completion failed");
                emitter.fail(e);
            }
        });
    }

    /**
     * Streaming chat completion that writes directly to an output stream
     * This is more efficient for HTTP SSE responses
     */
    public void chatCompletionStreamToOutput(
            OpenAIModels.ChatCompletionRequest request,
            OutputStream output) {
        try {
            String copilotToken = authService.getCopilotToken();
            
            copilotClient.chatCompletionStream(copilotToken, request, data -> {
                try {
                    output.write(("data: " + data + "\n\n").getBytes());
                    output.flush();
                } catch (Exception e) {
                    Log.errorf(e, "Error writing to output stream");
                    throw new RuntimeException(e);
                }
            });
            
            output.write("data: [DONE]\n\n".getBytes());
            output.flush();
        } catch (Exception e) {
            Log.errorf(e, "Streaming to output failed");
            throw new RuntimeException("Streaming to output failed", e);
        }
    }

    /**
     * Convert streaming response to non-streaming (for clients that don't support streaming)
     */
    public Uni<OpenAIModels.ChatCompletionResponse> chatCompletionFromStream(OpenAIModels.ChatCompletionRequest request) {
        String model = (request.model() == null || request.model().isEmpty()) 
            ? config.defaultModel() 
            : request.model();
        String id = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        long created = System.currentTimeMillis() / 1000;
        
        StringBuilder content = new StringBuilder();
        List<OpenAIModels.ToolCall> toolCalls = new ArrayList<>();
        String[] finishReason = {null};
        
        return chatCompletionStream(request)
            .onItem().transform(chunk -> {
                if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                    var delta = chunk.choices().get(0).delta();
                    if (delta.content() != null) {
                        content.append(delta.content());
                    }
                    if (delta.toolCalls() != null) {
                        toolCalls.addAll(delta.toolCalls());
                    }
                    var fr = chunk.choices().get(0).finishReason();
                    if (fr != null) {
                        finishReason[0] = fr;
                    }
                }
                return chunk;
            })
            .collect().last()
            .map(lastChunk -> {
                var message = new OpenAIModels.ChatMessage(
                    "assistant",
                    content.toString(),
                    toolCalls.isEmpty() ? null : toolCalls,
                    null,
                    null
                );
                
                return new OpenAIModels.ChatCompletionResponse(
                    id,
                    "chat.completion",
                    created,
                    model,
                    List.of(new OpenAIModels.ChatChoice(0, message, finishReason[0])),
                    null
                );
            });
    }
}
