package com.jcaa.events.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Table name is selected by trusted service configuration, never by a request. */
public class JdbcEventOutbox {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final String table;

  public JdbcEventOutbox(JdbcTemplate jdbc, ObjectMapper mapper, String table) {
    if (!java.util.Set.of("domain_event_outbox", "accounts.event_outbox").contains(table))
      throw new IllegalArgumentException("Unsupported outbox table.");
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.table = table;
  }

  public void enqueue(DomainEvent event) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Enqueue requires the business transaction.");
    }
    try {
      String entityId = event.payload().get(
          event.eventName().startsWith("account.") ? "accountId" : "userId");
      if (entityId == null || entityId.isBlank()) {
        throw new IllegalArgumentException("Event entity identifier is required.");
      }
      String entityKey = (event.eventName().startsWith("account.") ? "account:" : "user:")
          + entityId;
      // The counter row stays after deletion and is locked until the business transaction ends.
      Long version = jdbc.queryForObject(
          "INSERT INTO " + table + "_versions AS current (entity_key, version)"
              + " VALUES (?, 1) ON CONFLICT (entity_key)"
              + " DO UPDATE SET version = current.version + 1 RETURNING version",
          Long.class, entityKey);
      var payload = new HashMap<>(event.payload());
      payload.put("projectionVersion", Long.toString(version));
      DomainEvent versioned = new DomainEvent(
          event.schemaVersion(), event.eventId(), event.eventName(), event.occurredOn(), payload);
      jdbc.update(
          "INSERT INTO " + table + " (event_id, event_json) VALUES (?, ?)",
          event.eventId(),
          mapper.writeValueAsString(versioned));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not encode event.", exception);
    }
  }

  public Optional<Pending> next() {
    return jdbc
        .query(
            "SELECT event_id, event_json, attempts FROM "
                + table
                + " WHERE next_attempt_at <= CURRENT_TIMESTAMP ORDER BY created_at LIMIT 1 FOR"
                + " UPDATE SKIP LOCKED",
            (row, index) ->
                new Pending(
                    UUID.fromString(row.getString("event_id")),
                    row.getString("event_json"),
                    row.getInt("attempts")))
        .stream()
        .findFirst();
  }

  public void delete(UUID id) {
    jdbc.update("DELETE FROM " + table + " WHERE event_id = ?", id);
  }

  public void retry(Pending pending) {
    long seconds = Math.min(900, 5L << Math.min(pending.attempts(), 7));
    jdbc.update(
        "UPDATE " + table + " SET attempts = attempts + 1, next_attempt_at = ? WHERE event_id = ?",
        Timestamp.from(Instant.now().plusSeconds(seconds)),
        pending.id());
  }

  public record Pending(UUID id, String json, int attempts) {
    @Override
    public String toString() {
      return "Pending[id=" + id + ", attempts=" + attempts + "]";
    }
  }
}
