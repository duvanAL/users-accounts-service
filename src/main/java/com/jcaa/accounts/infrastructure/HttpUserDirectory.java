package com.jcaa.accounts.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.jcaa.accounts.application.UserDirectory;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpUserDirectory implements UserDirectory {
  private final RestClient client;

  public HttpUserDirectory(
      @Value("${USERS_API_URL:http://localhost:8080}") String baseUrl,
      org.springframework.core.env.Environment environment) {
    boolean local = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"));
    if (!local && !baseUrl.startsWith("https://") && !baseUrl.startsWith("http://localhost:"))
      throw new IllegalArgumentException("USERS_API_URL must use HTTPS outside localhost.");
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(60));
    client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  public UserContact get(String userId, String authorization) {
    try {
      JsonNode user =
          client
              .get()
              .uri("/api/users/{id}", userId)
              .header("Authorization", authorization)
              .retrieve()
              .body(JsonNode.class);
      if (user == null
          || !userId.equals(user.path("id").asText())
          || user.path("name").asText().isBlank()
          || user.path("email").asText().isBlank())
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "Invalid user directory response.");
      return new UserContact(userId, user.path("name").asText(), user.path("email").asText());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404)
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY, "The user does not exist.");
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User directory unavailable.");
    } catch (RestClientException exception) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "User directory unavailable.");
    }
  }
}
