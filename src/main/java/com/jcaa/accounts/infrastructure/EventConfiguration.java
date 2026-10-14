package com.jcaa.accounts.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.jdbc.JdbcEventOutbox;
import com.jcaa.events.jdbc.OutboxRelay;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class EventConfiguration {
  @Bean
  com.jcaa.accounts.application.DomainEventPublisher accountEventPublisher(JdbcEventOutbox outbox) {
    return outbox::enqueue;
  }

  @Bean
  JdbcEventOutbox eventOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcEventOutbox(jdbc, mapper, "accounts.event_outbox");
  }

  @Bean
  @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
  OutboxRelay eventRelay(
      JdbcEventOutbox outbox,
      KafkaTemplate<String, String> kafka,
      @Value("${KAFKA_TOPIC_NOTIFICATION_REQUESTED:user.notification.requested}") String topic,
      @Value("${KAFKA_SEND_TIMEOUT_MS:10000}") int timeout) {
    return new OutboxRelay(
        outbox,
        (key, json) -> {
          try {
            kafka.send(topic, key, json).get(timeout, TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka send interrupted.", e);
          } catch (Exception e) {
            throw new IllegalStateException("Kafka acknowledgement failed.", e);
          }
        });
  }
}
