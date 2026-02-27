package com.github.copilot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.config.CopilotConfig;
import com.github.copilot.model.OpenAIModels;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * REST Client for GitHub Copilot API
 */
@ApplicationScoped
public class CopilotClient {

    @Inject
    CopilotConfig config;

    @Inject
    ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_2)
            .build();
    }

    /**
     * Call Copilot Chat Completions API (non-streaming)
     */
    public OpenAIModels.ChatCompletionResponse chatCompletion(
            String copilotToken,
            OpenAIModels.ChatCompletionRequest request) throws IOException, InterruptedException {
        
        String url = config.getApiBaseUrl() + "/chat/completions";
        String body = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .headers(buildHeadersArray(copilotToken))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            Log.errorf("Chat completion failed: %d - %s", response.statusCode(), response.body());
            throw new RuntimeException("Chat completion failed: " + response.body());
        }
        
        return objectMapper.readValue(response.body(), OpenAIModels.ChatCompletionResponse.class);
    }

    /**
     * Call Copilot Chat Completions API with streaming callback
     * Processes SSE events as they arrive
     */
    public void chatCompletionStream(
            String copilotToken,
            OpenAIModels.ChatCompletionRequest request,
            Consumer<String> chunkConsumer) throws IOException, InterruptedException {
        
        String url = config.getApiBaseUrl() + "/chat/completions";
        
        // Ensure stream is true
        Map<String, Object> requestMap = objectMapper.convertValue(request, Map.class);
        requestMap.put("stream", true);
        String body = objectMapper.writeValueAsString(requestMap);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .headers(buildHeadersArray(copilotToken))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMinutes(5))
            .build();

        HttpResponse<InputStream> response = httpClient.send(
            httpRequest, 
            HttpResponse.BodyHandlers.ofInputStream()
        );
        
        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes());
            Log.errorf("Streaming chat completion failed: %d - %s", response.statusCode(), errorBody);
            throw new RuntimeException("Streaming chat completion failed: " + errorBody);
        }
        
        // Read SSE stream line by line
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    chunkConsumer.accept(data);
                    
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Call Copilot Chat Completions API (streaming) - returns raw SSE string
     * @deprecated Use chatCompletionStream with callback for better memory efficiency
     */
    @Deprecated
    public HttpResponse<String> chatCompletionStreamRaw(
            String copilotToken,
            OpenAIModels.ChatCompletionRequest request) throws IOException, InterruptedException {
        
        String url = config.getApiBaseUrl() + "/chat/completions";
        
        // Ensure stream is true
        Map<String, Object> requestMap = objectMapper.convertValue(request, Map.class);
        requestMap.put("stream", true);
        String body = objectMapper.writeValueAsString(requestMap);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .headers(buildHeadersArray(copilotToken))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMinutes(5))
            .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Call Copilot Embeddings API
     */
    public OpenAIModels.EmbeddingResponse embeddings(
            String copilotToken,
            OpenAIModels.EmbeddingRequest request) throws IOException, InterruptedException {
        
        String url = config.getApiBaseUrl() + "/embeddings";
        String body = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .headers(buildHeadersArray(copilotToken))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            Log.errorf("Embeddings failed: %d - %s", response.statusCode(), response.body());
            throw new RuntimeException("Embeddings failed: " + response.body());
        }
        
        return objectMapper.readValue(response.body(), OpenAIModels.EmbeddingResponse.class);
    }

    /**
     * Get available models from Copilot
     */
    public OpenAIModels.ModelsResponse getModels(String copilotToken) throws IOException, InterruptedException {
        String url = config.getApiBaseUrl() + "/models";
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .headers(buildHeadersArray(copilotToken))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            Log.errorf("Get models failed: %d - %s", response.statusCode(), response.body());
            throw new RuntimeException("Get models failed: " + response.body());
        }
        
        return objectMapper.readValue(response.body(), OpenAIModels.ModelsResponse.class);
    }

    private String[] buildHeadersArray(String copilotToken) {
        Map<String, String> headers = config.buildApiHeaders(copilotToken);
        return headers.entrySet().stream()
            .flatMap(e -> List.of(e.getKey(), e.getValue()).stream())
            .toArray(String[]::new);
    }
}