package com.github.copilot.config;

import io.smallrye.config.ConfigMapping;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "copilot")
public interface CopilotConfig {

    String accountType();

    OAuth oauth();

    GitHub github();

    String tokenUrl();

    Api api();

    String defaultModel();

    Headers headers();

    int tokenRefreshBuffer();

    interface OAuth {
        String clientId();
        String scopes();
    }

    interface GitHub {
        String deviceCodeUrl();
        String accessTokenUrl();
        String verificationUrl();
    }

    interface Api {
        String individual();
        String business();
        String enterprise();
    }

    interface Headers {
        String editorVersion();
        String editorPluginVersion();
        String copilotIntegrationId();
        String userAgent();
        String openaiIntent();
    }

    /**
     * Get the API base URL based on account type
     */
    default String getApiBaseUrl() {
        return switch (accountType().toLowerCase()) {
            case "enterprise" -> api().enterprise();
            case "business" -> api().business();
            default -> api().individual();
        };
    }

    /**
     * Build headers required for Copilot API requests
     */
    default Map<String, String> buildApiHeaders(String copilotToken) {
        return Map.of(
            "Authorization", "Bearer " + copilotToken,
            "Content-Type", "application/json",
            "Editor-Version", headers().editorVersion(),
            "Editor-Plugin-Version", headers().editorPluginVersion(),
            "Copilot-Integration-Id", headers().copilotIntegrationId(),
            "User-Agent", headers().userAgent(),
            "Openai-Intent", headers().openaiIntent()
        );
    }

    /**
     * Build headers for GitHub API requests (without Copilot token)
     */
    default Map<String, String> buildGitHubHeaders() {
        return Map.of(
            "Content-Type", "application/json",
            "Accept", "application/json",
            "Editor-Version", headers().editorVersion(),
            "User-Agent", headers().userAgent()
        );
    }
}