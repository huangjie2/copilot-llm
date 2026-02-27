package com.github.copilot.resource;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main application lifecycle handler
 */
@ApplicationScoped
public class ApplicationLifecycle {

    @Inject
    OpenAIResource openAIResource;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;

    void onStart(@Observes StartupEvent ev) {
        Log.info("========================================");
        Log.info("GitHub Copilot API Proxy Started");
        Log.info("========================================");
        Log.infof("API Base URL: http://localhost:%d/v1", httpPort);
        Log.info("");
        Log.info("OpenAI-compatible Endpoints:");
        Log.infof("  POST http://localhost:%d/v1/chat/completions", httpPort);
        Log.infof("  POST http://localhost:%d/v1/embeddings", httpPort);
        Log.infof("  GET  http://localhost:%d/v1/models", httpPort);
        Log.info("");
        Log.info("Authentication Endpoints:");
        Log.infof("  POST http://localhost:%d/v1/auth/device   - Start OAuth device flow", httpPort);
        Log.infof("  POST http://localhost:%d/v1/auth/poll    - Poll for auth completion", httpPort);
        Log.infof("  POST http://localhost:%d/v1/auth/token   - Set GitHub token directly", httpPort);
        Log.infof("  GET  http://localhost:%d/v1/auth/status  - Check auth status", httpPort);
        Log.info("");
        Log.info("Usage with curl:");
        Log.infof("  # Start device flow: curl -X POST http://localhost:%d/v1/auth/device", httpPort);
        Log.infof("  # Set token: curl -X POST http://localhost:%d/v1/auth/token -H 'Content-Type: application/json' -d '{\"token\":\"gho_xxx\"}'", httpPort);
        Log.infof("  # Chat: curl -X POST http://localhost:%d/v1/chat/completions -H 'Content-Type: application/json' -d '{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}'", httpPort);
        Log.info("========================================");
    }

    void onStop(@Observes ShutdownEvent ev) {
        Log.info("GitHub Copilot API Proxy Stopped");
    }
}
