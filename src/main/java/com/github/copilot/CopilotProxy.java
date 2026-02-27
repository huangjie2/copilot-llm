package com.github.copilot;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;

/**
 * Main application entry point
 */
public class CopilotProxy implements QuarkusApplication {

    @Override
    public int run(String... args) throws Exception {
        Quarkus.waitForExit();
        return 0;
    }
}
