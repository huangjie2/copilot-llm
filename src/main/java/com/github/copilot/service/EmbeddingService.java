package com.github.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.client.CopilotClient;
import com.github.copilot.config.CopilotConfig;
import com.github.copilot.model.OpenAIModels;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service for Copilot Embeddings
 */
@ApplicationScoped
public class EmbeddingService {

    @Inject
    CopilotClient copilotClient;

    @Inject
    AuthService authService;

    @Inject
    CopilotConfig config;

    @Inject
    ObjectMapper objectMapper;

    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-ada-002";

    /**
     * Generate embeddings for the given input
     * Input can be:
     * - A single string
     * - An array of strings
     * - An array of integers (tokens)
     * - An array of arrays of integers (multiple token arrays)
     */
    public OpenAIModels.EmbeddingResponse createEmbeddings(OpenAIModels.EmbeddingRequest request) {
        try {
            String copilotToken = authService.getCopilotToken();
            
            // Apply default model if not specified
            String model = (request.model() == null || request.model().isEmpty())
                ? DEFAULT_EMBEDDING_MODEL
                : request.model();
            
            // Build request with model and original input (don't normalize - let API handle it)
            var finalRequest = new OpenAIModels.EmbeddingRequest(
                model,
                request.input(),
                request.encodingFormat()
            );
            
            return copilotClient.embeddings(copilotToken, finalRequest);
        } catch (Exception e) {
            Log.errorf(e, "Embeddings failed");
            throw new RuntimeException("Embeddings failed", e);
        }
    }
}
