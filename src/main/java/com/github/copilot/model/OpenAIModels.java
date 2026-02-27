package com.github.copilot.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible request/response models
 */
public class OpenAIModels {

    // ============ Chat Completion Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        Boolean stream,
        Object stop,
        List<Tool> tools,
        @JsonProperty("tool_choice") Object toolChoice,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        @JsonProperty("presence_penalty") Double presencePenalty
    ) {
        public ChatCompletionRequest {
            if (stream == null) stream = false;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
        String role,
        Object content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId,
        String name
    ) {
        public ChatMessage(String role, String content) {
            this(role, content, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<ChatChoice> choices,
        Usage usage
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatChoice(
        int index,
        ChatMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    // ============ Streaming Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        List<ChunkChoice> choices,
        Usage usage
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChunkChoice(
        int index,
        DeltaMessage delta,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeltaMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls
    ) {}

    // ============ Embedding Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingRequest(
        String model,
        Object input,
        @JsonProperty("encoding_format") String encodingFormat
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingResponse(
        String object,
        List<EmbeddingData> data,
        String model,
        Usage usage
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingData(
        String object,
        int index,
        List<Float> embedding
    ) {}

    // ============ Tool/Function Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(
        String type,
        ToolFunction function
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolFunction(
        String name,
        String description,
        Map<String, Object> parameters
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(
        String id,
        String type,
        ToolCallFunction function
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCallFunction(
        String name,
        String arguments
    ) {}

    // ============ Common Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens
    ) {}

    // ============ Models List ============

    public record ModelsResponse(
        String object,
        List<ModelData> data
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ModelData(
        String id,
        String object,
        long created,
        @JsonProperty("owned_by") String ownedBy
    ) {}

    // ============ Error Models ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
        ErrorData error
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorData(
        String message,
        String type,
        String code
    ) {}
}
