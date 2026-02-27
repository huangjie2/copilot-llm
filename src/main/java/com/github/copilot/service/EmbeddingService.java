package com.github.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.client.CopilotClient;
import com.github.copilot.config.CopilotConfig;
import com.github.copilot.model.OpenAIModels;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

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
     */
    public OpenAIModels.EmbeddingResponse createEmbeddings(OpenAIModels.EmbeddingRequest request) {
        try {
            String copilotToken = authService.getCopilotToken();
            
            // Apply default model if not specified
            String model = (request.model() == null || request.model().isEmpty())
                ? DEFAULT_EMBEDDING_MODEL
                : request.model();
            
            // Normalize input to list format
            List<String> inputs = normalizeInput(request.input());
            
            // Process each input
            List<OpenAIModels.EmbeddingData> dataList = new ArrayList<>();
            int totalPromptTokens = 0;
            
            for (int i = 0; i < inputs.size(); i++) {
                var singleRequest = new OpenAIModels.EmbeddingRequest(
                    model,
                    inputs.get(i),
                    request.encodingFormat()
                );
                
                var response = copilotClient.embeddings(copilotToken, singleRequest);
                
                if (response.data() != null && !response.data().isEmpty()) {
                    dataList.add(new OpenAIModels.EmbeddingData(
                        "embedding",
                        i,
                        response.data().get(0).embedding()
                    ));
                }
                
                if (response.usage() != null && response.usage().promptTokens() != null) {
                    totalPromptTokens += response.usage().promptTokens();
                }
            }
            
            return new OpenAIModels.EmbeddingResponse(
                "list",
                dataList,
                model,
                new OpenAIModels.Usage(totalPromptTokens, 0, totalPromptTokens)
            );
        } catch (Exception e) {
            Log.errorf(e, "Embeddings failed");
            throw new RuntimeException("Embeddings failed", e);
        }
    }

    /**
     * Normalize input to a list of strings
     */
    @SuppressWarnings("unchecked")
    private List<String> normalizeInput(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        
        if (input instanceof String) {
            return List.of((String) input);
        }
        
        if (input instanceof List) {
            List<?> list = (List<?>) input;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    result.add(item.toString());
                }
            }
            return result;
        }
        
        // Handle array
        if (input.getClass().isArray()) {
            Object[] array = (Object[]) input;
            List<String> result = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    result.add(item.toString());
                }
            }
            return result;
        }
        
        throw new IllegalArgumentException("Input must be a string or array of strings");
    }
}
