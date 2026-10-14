package com.jcaa.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.accounts.application.UserDirectory;
import com.jcaa.events.DomainEvent;
import com.jcaa.events.jdbc.JdbcEventOutbox;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:accounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.sql.init.mode=always",
      "app.kafka.enabled=false",
      "JWT_SECRET=dGVzdC1vbmx5LWp3dC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0zMi1ieXRlcw=="
    })
@AutoConfigureMockMvc
class AccountIntegrationTest {
  private static final String SECRET =
      "dGVzdC1vbmx5LWp3dC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0zMi1ieXRlcw==";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;
  @MockBean UserDirectory users;
  @SpyBean JdbcEventOutbox outbox;

  @BeforeEach
  void resetData() {
    jdbc.update("DELETE FROM accounts.event_outbox");
    jdbc.update("DELETE FROM accounts.accounts");
  }

  static String bearer(String role) {
    return "Bearer "
        + Jwts.builder()
            .subject("demo-admin")
            .claim("role", role)
            .expiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
            .compact();
  }

  @Test
  void createReadAndPublishableEventUseAnIndependentSchema() throws Exception {
    when(users.get(eq("usr-1"), anyString()))
        .thenReturn(new UserDirectory.UserContact("usr-1", "Demo", "demo@example.test"));
    String raw =
        mvc.perform(
                post("/api/accounts")
                    .header("Authorization", bearer("ADMIN"))
                    .contentType("application/json")
                    .content("{\"userId\":\"usr-1\",\"accountType\":\"SAVINGS\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accountType").value("SAVINGS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(raw).path("id").asText();
    mvc.perform(get("/api/accounts/" + id).header("Authorization", bearer("REVIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("usr-1"));
    mvc.perform(
            get("/api/accounts").param("userId", "usr-1").header("Authorization", bearer("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    DomainEvent event =
        mapper.readValue(
            jdbc.queryForObject("SELECT event_json FROM accounts.event_outbox", String.class),
            DomainEvent.class);
    assertThat(event.eventName()).isEqualTo("account.created");
    assertThat(event.payload()).containsEntry("accountId", id).containsEntry("userId", "usr-1");
  }

  @Test
  void failedOutboxRollsBackAccountInsert() {
    when(users.get(eq("usr-1"), anyString()))
        .thenReturn(new UserDirectory.UserContact("usr-1", "Demo", "demo@example.test"));
    doThrow(new IllegalStateException("simulated outbox unavailable")).when(outbox).enqueue(any());
    org.junit.jupiter.api.Assertions.assertThrows(
        Exception.class,
        () ->
            mvc.perform(
                post("/api/accounts")
                    .header("Authorization", bearer("ADMIN"))
                    .contentType("application/json")
                    .content("{\"userId\":\"usr-1\",\"accountType\":\"SAVINGS\"}")));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts.accounts", Integer.class))
        .isZero();
  }

  @Test
  void securityAndValidationRejectInvalidRequests() throws Exception {
    mvc.perform(post("/api/accounts").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/accounts")
                .header("Authorization", bearer("MEMBER"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/accounts")
                .header("Authorization", bearer("ADMIN"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/accounts/00000000-0000-0000-0000-000000000001")
                .header("Authorization", bearer("REVIEWER")))
        .andExpect(status().isNotFound());
    verifyNoInteractions(users);
  }

  @Test
  void outboxRetainsFailedEventsAndDeletesOnlyAfterConfirmation() {
    DomainEvent event = DomainEvent.create("account.created", java.util.Map.of("userId", "usr-1"));
    outbox.enqueue(event);
    new com.jcaa.events.jdbc.OutboxRelay(
            outbox,
            (key, body) -> {
              throw new IllegalStateException("broker down");
            })
        .publishNext();
    assertThat(jdbc.queryForObject("SELECT attempts FROM accounts.event_outbox", Integer.class))
        .isEqualTo(1);
    assertThat(outbox.next()).isEmpty();
    jdbc.update("UPDATE accounts.event_outbox SET next_attempt_at = CURRENT_TIMESTAMP");
    java.util.List<String> keys = new java.util.ArrayList<>();
    new com.jcaa.events.jdbc.OutboxRelay(outbox, (key, body) -> keys.add(key)).publishNext();
    assertThat(keys).containsExactly(event.eventId().toString());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts.event_outbox", Integer.class))
        .isZero();
  }

  @Test
  void missingUserCannotCreateAnOrphanAccount() throws Exception {
    when(users.get(eq("missing"), anyString()))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
    mvc.perform(
            post("/api/accounts")
                .header("Authorization", bearer("ADMIN"))
                .contentType("application/json")
                .content("{\"userId\":\"missing\",\"accountType\":\"SAVINGS\"}"))
        .andExpect(status().isUnprocessableEntity());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts.accounts", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts.event_outbox", Integer.class))
        .isZero();
  }
}
