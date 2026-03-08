package com.github.copilot.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.client.CopilotClient;
import com.github.copilot.config.GitHubClient;
import com.github.copilot.model.OpenAIModels;
import com.github.copilot.service.AuthService;
import com.github.copilot.service.ChatService;
import com.github.copilot.service.EmbeddingService;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OpenAI-compatible REST API endpoints
 */
@Path("/v1")
@Tag(name = "OpenAI Compatible API", description = "OpenAI-compatible endpoints for GitHub Copilot")
public class OpenAIResource {

    @Inject
    AuthService authService;

    @Inject
    ChatService chatService;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    CopilotClient copilotClient;

    @Inject
    ObjectMapper objectMapper;

    // ============ Chat Completions ============

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, "text/event-stream"})
    @Operation(summary = "Create chat completion", description = "Creates a model response for the given chat conversation")
    @APIResponse(responseCode = "200", description = "Successful response", 
        content = @Content(schema = @Schema(implementation = OpenAIModels.ChatCompletionResponse.class)))
    public Response chatCompletion(OpenAIModels.ChatCompletionRequest request) {
        try {
            boolean wantsStreaming = Boolean.TRUE.equals(request.stream());
            
            if (wantsStreaming) {
                StreamingOutput streamingOutput = output -> 
                    chatService.chatCompletionStreamToOutput(request, output);
                    
                return Response
                    .ok(streamingOutput)
                    .type("text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("X-Accel-Buffering", "no")
                    .build();
            } else {
                var response = chatService.chatCompletion(request);
                return Response.ok(response).build();
            }
        } catch (Exception e) {
            Log.errorf(e, "Chat completion failed");
            return Response.serverError()
                .entity(new OpenAIModels.ErrorResponse(
                    new OpenAIModels.ErrorData(e.getMessage(), "server_error", null)
                ))
                .build();
        }
    }

    // ============ Responses API (OpenAI Responses API compatible) ============

    @POST
    @Path("/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, "text/event-stream"})
    @Operation(summary = "Create response", description = "Creates a model response using the Responses API format")
    @APIResponse(responseCode = "200", description = "Successful response")
    public Response createResponse(OpenAIModels.ResponsesRequest request) {
        try {
            // Convert ResponsesRequest to ChatCompletionRequest
            OpenAIModels.ChatCompletionRequest chatRequest = convertToChatRequest(request);
            
            boolean wantsStreaming = Boolean.TRUE.equals(request.stream());
            
            if (wantsStreaming) {
                StreamingOutput streamingOutput = output -> 
                    streamResponsesOutput(chatRequest, output);
                    
                return Response
                    .ok(streamingOutput)
                    .type("text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("X-Accel-Buffering", "no")
                    .build();
            } else {
                var chatResponse = chatService.chatCompletion(chatRequest);
                var response = convertToResponsesResponse(chatResponse);
                return Response.ok(response).build();
            }
        } catch (Exception e) {
            Log.errorf(e, "Responses API failed");
            return Response.serverError()
                .entity(new OpenAIModels.ErrorResponse(
                    new OpenAIModels.ErrorData(e.getMessage(), "server_error", null)
                ))
                .build();
        }
    }

    /**
     * Convert ResponsesRequest to ChatCompletionRequest
     */
    private OpenAIModels.ChatCompletionRequest convertToChatRequest(OpenAIModels.ResponsesRequest request) {
        List<OpenAIModels.ChatMessage> messages = new ArrayList<>();
        
        // Add instructions as system message if present
        if (request.instructions() != null && !request.instructions().isEmpty()) {
            messages.add(new OpenAIModels.ChatMessage("system", request.instructions()));
        }
        
        // Handle input - can be String or List
        if (request.input() instanceof String inputStr) {
            messages.add(new OpenAIModels.ChatMessage("user", inputStr));
        } else if (request.input() instanceof List<?> inputList) {
            for (Object item : inputList) {
                if (item instanceof Map<?, ?> map) {
                    String role = (String) map.get("role");
                    Object content = map.get("content");
                    messages.add(new OpenAIModels.ChatMessage(role, content, null, null, null));
                } else if (item instanceof OpenAIModels.ResponsesInputItem inputItem) {
                    messages.add(new OpenAIModels.ChatMessage(
                        inputItem.role(), 
                        inputItem.content(), 
                        null, null, null
                    ));
                }
            }
        }
        
        // Convert tools if present
        List<OpenAIModels.Tool> tools = null;
        if (request.tools() != null && !request.tools().isEmpty()) {
            tools = request.tools().stream()
                .map(t -> new OpenAIModels.Tool(t.type(), 
                    new OpenAIModels.ToolFunction(t.name(), t.description(), t.parameters())))
                .toList();
        }
        
        return new OpenAIModels.ChatCompletionRequest(
            request.model(),
            messages,
            request.maxOutputTokens(),
            request.temperature(),
            request.topP(),
            request.stream(),
            null,
            tools,
            null,
            null,
            null
        );
    }

    /**
     * Convert ChatCompletionResponse to ResponsesResponse
     */
    private OpenAIModels.ResponsesResponse convertToResponsesResponse(OpenAIModels.ChatCompletionResponse chatResponse) {
        String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        
        List<OpenAIModels.ResponsesOutputItem> outputItems = new ArrayList<>();
        
        if (chatResponse.choices() != null && !chatResponse.choices().isEmpty()) {
            var choice = chatResponse.choices().get(0);
            var message = choice.message();
            
            String itemId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            
            List<OpenAIModels.ResponsesContent> contentList = new ArrayList<>();
            if (message.content() != null) {
                contentList.add(new OpenAIModels.ResponsesContent(
                    "output_text",
                    message.content().toString(),
                    null
                ));
            }
            
            outputItems.add(new OpenAIModels.ResponsesOutputItem(
                itemId,
                "message",
                "completed",
                "assistant",
                contentList,
                null
            ));
        }
        
        return new OpenAIModels.ResponsesResponse(
            responseId,
            "response",
            chatResponse.created(),
            chatResponse.model(),
            "completed",
            outputItems,
            chatResponse.usage()
        );
    }

    /**
     * Stream Responses API output
     */
    private void streamResponsesOutput(OpenAIModels.ChatCompletionRequest request, java.io.OutputStream output) {
        try {
            String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String itemId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            long createdAt = System.currentTimeMillis() / 1000;
            String model = request.model() != null ? request.model() : "gpt-5-mini";
            
            // Send initial response event with role
            var initialItem = new OpenAIModels.ResponsesOutputItem(
                itemId,
                "message",
                "in_progress",
                "assistant",
                List.of(new OpenAIModels.ResponsesContent("output_text", "", null)),
                null
            );
            var initialResponse = new OpenAIModels.ResponsesResponse(
                responseId, "response", createdAt, model, "in_progress",
                List.of(initialItem), null
            );
            output.write(("data: " + objectMapper.writeValueAsString(initialResponse) + "\n\n").getBytes());
            output.flush();
            
            // Stream content
            StringBuilder content = new StringBuilder();
            chatService.chatCompletionStream(request)
                .subscribe().with(chunk -> {
                    try {
                        if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                            var delta = chunk.choices().get(0).delta();
                            if (delta.content() != null) {
                                content.append(delta.content());
                                
                                var outputItem = new OpenAIModels.ResponsesOutputItem(
                                    itemId,
                                    "message",
                                    "in_progress",
                                    "assistant",
                                    List.of(new OpenAIModels.ResponsesContent("output_text", content.toString(), null)),
                                    null
                                );
                                var streamResponse = new OpenAIModels.ResponsesResponse(
                                    responseId, "response", createdAt, model, "in_progress",
                                    List.of(outputItem), null
                                );
                                output.write(("data: " + objectMapper.writeValueAsString(streamResponse) + "\n\n").getBytes());
                                output.flush();
                            }
                            
                            // Check for completion
                            if (chunk.choices().get(0).finishReason() != null) {
                                var finalItem = new OpenAIModels.ResponsesOutputItem(
                                    itemId,
                                    "message",
                                    "completed",
                                    "assistant",
                                    List.of(new OpenAIModels.ResponsesContent("output_text", content.toString(), null)),
                                    null
                                );
                                var finalResponse = new OpenAIModels.ResponsesResponse(
                                    responseId, "response", createdAt, model, "completed",
                                    List.of(finalItem), chunk.usage()
                                );
                                output.write(("data: " + objectMapper.writeValueAsString(finalResponse) + "\n\n").getBytes());
                                output.flush();
                            }
                        }
                    } catch (Exception e) {
                        Log.errorf(e, "Error writing streaming response");
                    }
                });
            
            output.write("data: [DONE]\n\n".getBytes());
            output.flush();
        } catch (Exception e) {
            Log.errorf(e, "Streaming responses failed");
            throw new RuntimeException("Streaming responses failed", e);
        }
    }

    // ============ Embeddings ============

    @POST
    @Path("/embeddings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create embeddings", description = "Creates an embedding vector representing the input text")
    @APIResponse(responseCode = "200", description = "Successful response",
        content = @Content(schema = @Schema(implementation = OpenAIModels.EmbeddingResponse.class)))
    public Response embeddings(OpenAIModels.EmbeddingRequest request) {
        try {
            var response = embeddingService.createEmbeddings(request);
            return Response.ok(response).build();
        } catch (Exception e) {
            Log.errorf(e, "Embeddings failed");
            return Response.serverError()
                .entity(new OpenAIModels.ErrorResponse(
                    new OpenAIModels.ErrorData(e.getMessage(), "server_error", null)
                ))
                .build();
        }
    }

    // ============ Models ============

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List models", description = "Lists the currently available models from Copilot API")
    public Response listModels() {
        try {
            if (authService.isAuthenticated()) {
                // Fetch real models from Copilot API
                OpenAIModels.ModelsResponse models = copilotClient.getModels(authService.getCopilotToken());
                return Response.ok(models).build();
            } else {
                // Fallback to static list when not authenticated
                Log.info("Not authenticated, returning static model list");
                return Response.ok(getStaticModels()).build();
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to fetch models from Copilot API, using fallback");
            return Response.ok(getStaticModels()).build();
        }
    }

    @GET
    @Path("/models/{modelId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Retrieve model", description = "Retrieves a model instance from Copilot API")
    public Response retrieveModel(@PathParam("modelId") String modelId) {
        try {
            if (authService.isAuthenticated()) {
                // Fetch models and find the specific one
                OpenAIModels.ModelsResponse models = copilotClient.getModels(authService.getCopilotToken());
                Optional<OpenAIModels.ModelData> model = models.data().stream()
                    .filter(m -> m.id().equals(modelId))
                    .findFirst();
                
                if (model.isPresent()) {
                    return Response.ok(model.get()).build();
                }
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to fetch model from Copilot API");
        }
        
        // Fallback: create a basic model response
        return Response.ok(createModelData(modelId, "unknown")).build();
    }

    /**
     * Static fallback model list (used when not authenticated or API fails)
     * Updated: 2026-02-28
     * Source: https://docs.github.com/copilot/reference/ai-models/supported-models
     */
    private OpenAIModels.ModelsResponse getStaticModels() {
        var models = List.of(
            // OpenAI GPT-4.x series
            createModelData("gpt-4.1", "openai"),
            createModelData("gpt-4o", "openai"),
            
            // OpenAI GPT-5 series
            createModelData("gpt-5-mini", "openai"),
            createModelData("gpt-5.1", "openai"),
            createModelData("gpt-5.2", "openai"),
            
            // OpenAI GPT-5 Codex series (code-optimized)
            createModelData("gpt-5.1-codex", "openai"),
            createModelData("gpt-5.1-codex-mini", "openai"),
            createModelData("gpt-5.1-codex-max", "openai"),
            createModelData("gpt-5.2-codex", "openai"),
            
            // Anthropic Claude 4.x series
            createModelData("claude-haiku-4.5", "anthropic"),
            createModelData("claude-sonnet-4", "anthropic"),
            createModelData("claude-sonnet-4.5", "anthropic"),
            createModelData("claude-opus-4.5", "anthropic"),
            createModelData("claude-opus-4.6", "anthropic"),
            
            // Google Gemini series
            createModelData("gemini-2.5-pro", "google"),
            createModelData("gemini-3-flash", "google"),
            createModelData("gemini-3-pro", "google"),
            
            // xAI Grok series
            createModelData("grok-code-fast-1", "xai"),
            
            // Fine-tuned models
            createModelData("raptor-mini", "github")
        );
        return new OpenAIModels.ModelsResponse("list", models);
    }

    private OpenAIModels.ModelData createModelData(String id, String ownedBy) {
        return new OpenAIModels.ModelData(id, "model", System.currentTimeMillis() / 1000, ownedBy);
    }

    // ============ Authentication ============

    @POST
    @Path("/auth/device")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start device flow", description = "Starts OAuth device flow authentication")
    public Response startDeviceFlow() {
        try {
            GitHubClient.DeviceCodeResponse response = authService.startDeviceFlow();
            
            return Response.ok(new AuthStartResponse(
                response.device_code(),
                response.user_code(),
                response.verification_uri(),
                response.expires_in(),
                response.interval()
            )).build();
        } catch (Exception e) {
            Log.errorf(e, "Failed to start device flow");
            return Response.serverError()
                .entity(new OpenAIModels.ErrorResponse(
                    new OpenAIModels.ErrorData(e.getMessage(), "auth_error", null)
                ))
                .build();
        }
    }

    @POST
    @Path("/auth/poll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Poll for authentication", description = "Polls for device flow completion")
    public Response pollForAuth(PollAuthRequest request) {
        try {
            var token = authService.pollForAccessToken(request.deviceCode());
            
            if (token.isPresent()) {
                // Get Copilot token
                String copilotToken = authService.getCopilotToken();
                return Response.ok(new AuthPollResponse(true, "Authentication successful")).build();
            } else {
                return Response.ok(new AuthPollResponse(false, "Waiting for user authorization...")).build();
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to poll for auth");
            return Response.ok(new AuthPollResponse(false, e.getMessage())).build();
        }
    }

    @POST
    @Path("/auth/token")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set GitHub token", description = "Set GitHub token directly (if you have one)")
    public Response setToken(SetTokenRequest request) {
        try {
            authService.setGitHubToken(request.token());
            String copilotToken = authService.getCopilotToken();
            return Response.ok(new AuthPollResponse(true, "Token set successfully")).build();
        } catch (Exception e) {
            Log.errorf(e, "Failed to set token");
            return Response.serverError()
                .entity(new OpenAIModels.ErrorResponse(
                    new OpenAIModels.ErrorData(e.getMessage(), "auth_error", null)
                ))
                .build();
        }
    }

    @GET
    @Path("/auth/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get auth status", description = "Returns current authentication status")
    public Response getAuthStatus() {
        boolean authenticated = authService.isAuthenticated();
        return Response.ok(new AuthStatusResponse(authenticated)).build();
    }

    @POST
    @Path("/auth/logout")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Logout", description = "Clears stored authentication tokens")
    public Response logout() {
        authService.logout();
        return Response.ok(new AuthPollResponse(true, "Logged out successfully")).build();
    }

    // Auth DTOs
    public record AuthStartResponse(
        String device_code,
        String user_code,
        String verification_uri,
        int expires_in,
        int interval
    ) {}

    public record PollAuthRequest(String deviceCode) {}

    public record AuthPollResponse(boolean success, String message) {}

    public record SetTokenRequest(String token) {}

    public record AuthStatusResponse(boolean authenticated) {}
}