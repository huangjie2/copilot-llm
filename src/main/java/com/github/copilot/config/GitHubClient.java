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
 * GitHub OAuth REST Client for device flow authentication
 */
@Path("")
@RegisterRestClient(baseUri = "https://github.com")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface GitHubClient {

    /**
     * Request device code for OAuth device flow
     */
    @POST
    @Path("/login/device/code")
    @ClientHeaderParam(name = "Accept", value = "application/json")
    DeviceCodeResponse requestDeviceCode(DeviceCodeRequest request);

    /**
     * Exchange device code for access token
     */
    @POST
    @Path("/login/oauth/access_token")
    @ClientHeaderParam(name = "Accept", value = "application/json")
    AccessTokenResponse getAccessToken(AccessTokenRequest request);

    // Request/Response DTOs
    record DeviceCodeRequest(
        String client_id,
        String scope
    ) {}

    record DeviceCodeResponse(
        String device_code,
        String user_code,
        String verification_uri,
        int expires_in,
        int interval
    ) {}

    record AccessTokenRequest(
        String client_id,
        String device_code,
        String grant_type
    ) {
        public AccessTokenRequest(String clientId, String deviceCode) {
            this(clientId, deviceCode, "urn:ietf:params:oauth:grant-type:device_code");
        }
    }

    record AccessTokenResponse(
        String access_token,
        String token_type,
        String scope,
        String error,
        String error_description
    ) {}
}
