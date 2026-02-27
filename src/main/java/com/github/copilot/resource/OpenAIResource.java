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

import java.util.List;
import java.util.Optional;

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
            if (!authService.isAuthenticated()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new OpenAIModels.ErrorResponse(
                        new OpenAIModels.ErrorData("Not authenticated. Use /v1/auth/device to authenticate.", "auth_error", null)
                    ))
                    .build();
            }

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
            if (!authService.isAuthenticated()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new OpenAIModels.ErrorResponse(
                        new OpenAIModels.ErrorData("Not authenticated. Use /v1/auth/device to authenticate.", "auth_error", null)
                    ))
                    .build();
            }

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
     */
    private OpenAIModels.ModelsResponse getStaticModels() {
        var models = List.of(
            // OpenAI GPT-4.5 series
            createModelData("gpt-4.5-preview", "openai"),
            // OpenAI GPT-4o series
            createModelData("gpt-4o", "openai"),
            createModelData("gpt-4o-mini", "openai"),
            // OpenAI o1 reasoning series
            createModelData("o1", "openai"),
            createModelData("o1-mini", "openai"),
            createModelData("o1-preview", "openai"),
            createModelData("o1-pro", "openai"),
            // Anthropic Claude series
            createModelData("claude-3.7-sonnet", "anthropic"),
            createModelData("claude-3.5-sonnet", "anthropic"),
            createModelData("claude-3-opus", "anthropic"),
            // Google Gemini series
            createModelData("gemini-2.0-flash", "google"),
            createModelData("gemini-1.5-pro", "google")
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