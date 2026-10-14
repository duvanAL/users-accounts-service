package com.jcaa.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.jcaa.accounts.application.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

/** Uses real HTTP dispatching; MockMvc does not reproduce the protected /error dispatch. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:http-validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.sql.init.mode=always", "app.kafka.enabled=false",
    "JWT_SECRET=dGVzdC1vbmx5LWp3dC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0zMi1ieXRlcw=="
})
class AccountHttpValidationTest {
  @Autowired TestRestTemplate http;
  @MockBean UserDirectory users;

  @BeforeEach
  void useHttpClientWithoutAutomaticAuthenticationRetries() {
    http.getRestTemplate().setRequestFactory(
        new org.springframework.http.client.JdkClientHttpRequestFactory());
  }

  private ResponseEntity<String> post(String payload, String role) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (role != null) headers.set("Authorization", AccountIntegrationTest.bearer(role));
    return http.postForEntity("/api/accounts", new HttpEntity<>(payload, headers), String.class);
  }

  @Test
  void invalidAuthenticatedPayloadsReturn400WithoutErrorRedispatch() {
    for (String payload : new String[] {
        "{}", "{\"userId\":\"\",\"accountType\":\"SAVINGS\"}",
        "{\"userId\":\"usr-1\",\"accountType\":\"UNKNOWN\"}", "{broken"
    }) {
      ResponseEntity<String> response = post(payload, "ADMIN");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).contains("Invalid account request.")
          .doesNotContain("Authorization", "stackTrace", "JWT_SECRET");
    }
    verifyNoInteractions(users);
  }

  @Test
  void authenticationRemainsRequired() {
    assertThat(post("{}", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(users);
  }

  @Test
  void missingUserRetains422OverRealHttp() {
    when(users.get(eq("missing"), anyString())).thenThrow(
        new org.springframework.web.server.ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY));
    assertThat(post("{\"userId\":\"missing\",\"accountType\":\"SAVINGS\"}", "ADMIN")
        .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}

