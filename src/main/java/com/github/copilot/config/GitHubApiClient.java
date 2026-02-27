package com.github.copilot.config;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * GitHub API REST Client for Copilot token
 */
@Path("")
@RegisterRestClient(baseUri = "https://api.github.com")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubApiClient {

    /**
     * Get Copilot token using GitHub access token
     */
    @POST
    @Path("/copilot_internal/v2/token")
    @ClientHeaderParam(name = "Accept", value = "application/json")
    CopilotTokenResponse getCopilotToken(
        @HeaderParam("Authorization") String authorization
    );

    record CopilotTokenResponse(
        String token,
        long expires_at,
        int refresh_in
    ) {}
}