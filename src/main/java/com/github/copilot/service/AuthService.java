package com.github.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.config.CopilotConfig;
import com.github.copilot.config.GitHubApiClient;
import com.github.copilot.config.GitHubClient;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Authentication service for GitHub Copilot
 * Handles OAuth device flow and token management
 */
@ApplicationScoped
public class AuthService {

    @Inject
    CopilotConfig config;

    @Inject
    @RestClient
    GitHubClient gitHubClient;

    @Inject
    @RestClient
    GitHubApiClient gitHubApiClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "copilot.token-dir", defaultValue = "${user.home}/.config/copilot-proxy")
    String tokenDir;

    // Cached tokens
    private volatile String cachedCopilotToken;
    private volatile String cachedGitHubToken;
    private volatile long tokenExpiresAt;

    /**
     * Get the current Copilot token, refreshing if necessary
     */
    public String getCopilotToken() {
        if (isTokenValid()) {
            return cachedCopilotToken;
        }
        return refreshToken();
    }

    /**
     * Get the current GitHub token
     */
    public Optional<String> getGitHubToken() {
        if (cachedGitHubToken != null) {
            return Optional.of(cachedGitHubToken);
        }
        return loadGitHubToken();
    }

    /**
     * Set the GitHub token (from user input or file)
     */
    public void setGitHubToken(String token) {
        this.cachedGitHubToken = token;
        saveGitHubToken(token);
        // Clear cached Copilot token to force refresh
        this.cachedCopilotToken = null;
    }

    /**
     * Start OAuth device flow for authentication
     * Returns the device code response with user_code and verification URL
     */
    public GitHubClient.DeviceCodeResponse startDeviceFlow() {
        var request = new GitHubClient.DeviceCodeRequest(
            config.oauth().clientId(),
            config.oauth().scopes()
        );
        return gitHubClient.requestDeviceCode(request);
    }

    /**
     * Poll for access token using device code
     */
    public Optional<String> pollForAccessToken(String deviceCode) {
        try {
            var request = new GitHubClient.AccessTokenRequest(
                config.oauth().clientId(),
                deviceCode
            );
            var response = gitHubClient.getAccessToken(request);
            
            if (response.error() != null) {
                if ("authorization_pending".equals(response.error())) {
                    return Optional.empty();
                }
                Log.errorf("Access token error: %s - %s", response.error(), response.error_description());
                return Optional.empty();
            }
            
            if (response.access_token() != null) {
                setGitHubToken(response.access_token());
                return Optional.of(response.access_token());
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to poll for access token");
        }
        return Optional.empty();
    }

    /**
     * Perform complete device flow authentication
     * Blocks until user completes authentication or times out
     */
    public String authenticateWithDeviceFlow() throws InterruptedException {
        var deviceCodeResponse = startDeviceFlow();
        
        Log.infof("Please visit: %s", deviceCodeResponse.verification_uri());
        Log.infof("Enter code: %s", deviceCodeResponse.user_code());
        
        int interval = deviceCodeResponse.interval();
        int expiresInSeconds = deviceCodeResponse.expires_in();
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < expiresInSeconds * 1000L) {
            TimeUnit.SECONDS.sleep(interval);
            
            var token = pollForAccessToken(deviceCodeResponse.device_code());
            if (token.isPresent()) {
                Log.info("Authentication successful!");
                return refreshToken();
            }
        }
        
        throw new RuntimeException("Device flow authentication timed out");
    }

    /**
     * Refresh Copilot token using GitHub token
     */
    public synchronized String refreshToken() {
        var gitHubToken = getGitHubToken();
        if (gitHubToken.isEmpty()) {
            throw new RuntimeException("No GitHub token available. Please authenticate first.");
        }
        
        try {
            var response = gitHubApiClient.getCopilotToken("token " + gitHubToken.get());
            
            this.cachedCopilotToken = response.token();
            this.tokenExpiresAt = response.expires_at();
            
            // Save token to file
            saveCopilotToken(response.token(), response.expires_at());
            
            Log.infof("Copilot token refreshed, expires at: %s", 
                Instant.ofEpochSecond(response.expires_at()));
            
            return cachedCopilotToken;
        } catch (Exception e) {
            Log.errorf(e, "Failed to refresh Copilot token");
            throw new RuntimeException("Failed to refresh Copilot token", e);
        }
    }

    /**
     * Check if token is valid and not expired
     */
    private boolean isTokenValid() {
        if (cachedCopilotToken == null) {
            // Try to load from file
            var loaded = loadCopilotToken();
            if (loaded.isEmpty()) {
                return false;
            }
        }
        
        // Check if token expires soon
        long now = Instant.now().getEpochSecond();
        long buffer = config.tokenRefreshBuffer();
        return tokenExpiresAt > (now + buffer);
    }

    /**
     * Scheduled token refresh (every 5 minutes)
     */
    @Scheduled(every = "5m")
    void scheduledTokenRefresh() {
        if (cachedGitHubToken != null && !isTokenValid()) {
            try {
                refreshToken();
            } catch (Exception e) {
                Log.errorf(e, "Scheduled token refresh failed");
            }
        }
    }

    // ============ File Storage Methods ============

    private void saveGitHubToken(String token) {
        try {
            Path dir = Paths.get(tokenDir.replace("${user.home}", System.getProperty("user.home")));
            Files.createDirectories(dir);
            Path file = dir.resolve("github-token");
            Files.writeString(file, token);
            Log.debugf("GitHub token saved to %s", file);
        } catch (IOException e) {
            Log.errorf(e, "Failed to save GitHub token");
        }
    }

    private Optional<String> loadGitHubToken() {
        try {
            Path file = Paths.get(tokenDir.replace("${user.home}", System.getProperty("user.home")))
                .resolve("github-token");
            if (Files.exists(file)) {
                String token = Files.readString(file).trim();
                if (!token.isEmpty()) {
                    cachedGitHubToken = token;
                    return Optional.of(token);
                }
            }
        } catch (IOException e) {
            Log.debugf("Could not load GitHub token from file");
        }
        return Optional.empty();
    }

    private void saveCopilotToken(String token, long expiresAt) {
        try {
            Path dir = Paths.get(tokenDir.replace("${user.home}", System.getProperty("user.home")));
            Files.createDirectories(dir);
            Path file = dir.resolve("copilot-token.json");
            
            var data = new TokenData(token, expiresAt);
            Files.writeString(file, objectMapper.writeValueAsString(data));
        } catch (IOException e) {
            Log.errorf(e, "Failed to save Copilot token");
        }
    }

    private Optional<String> loadCopilotToken() {
        try {
            Path file = Paths.get(tokenDir.replace("${user.home}", System.getProperty("user.home")))
                .resolve("copilot-token.json");
            if (Files.exists(file)) {
                var data = objectMapper.readValue(Files.readString(file), TokenData.class);
                this.cachedCopilotToken = data.token();
                this.tokenExpiresAt = data.expiresAt();
                return Optional.of(data.token());
            }
        } catch (IOException e) {
            Log.debugf("Could not load Copilot token from file");
        }
        return Optional.empty();
    }

    public boolean isAuthenticated() {
        return getGitHubToken().isPresent();
    }

    public void logout() {
        cachedGitHubToken = null;
        cachedCopilotToken = null;
        tokenExpiresAt = 0;
        
        try {
            Path dir = Paths.get(tokenDir.replace("${user.home}", System.getProperty("user.home")));
            Files.deleteIfExists(dir.resolve("github-token"));
            Files.deleteIfExists(dir.resolve("copilot-token.json"));
        } catch (IOException e) {
            Log.errorf(e, "Failed to delete token files");
        }
    }

    // Token data record for JSON serialization
    record TokenData(String token, long expiresAt) {}
}