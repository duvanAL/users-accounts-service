package com.jcaa.events.jdbc;

import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once delivery: retain the same eventId across broker retries. */
public class OutboxRelay {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private final JdbcEventOutbox outbox;
  private final BiConsumer<String, String> publish;

  public OutboxRelay(JdbcEventOutbox outbox, BiConsumer<String, String> publish) {
    this.outbox = outbox;
    this.publish = publish;
  }

  @Scheduled(fixedDelayString = "${EVENT_OUTBOX_POLL_INTERVAL_MS:5000}")
  @Transactional
  public void publishNext() {
    outbox
        .next()
        .ifPresent(
            pending -> {
              try {
                publish.accept(pending.id().toString(), pending.json());
                outbox.delete(pending.id());
                LOG.info("Domain event published. id={}", pending.id());
              } catch (RuntimeException exception) {
                outbox.retry(pending);
                LOG.warn(
                    "Domain event awaiting broker retry. id={} error={}",
                    pending.id(),
                    exception.getClass().getSimpleName());
              }
            });
  }
}
