package com.jcaa.accounts.domain;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID id, String userId, Type accountType, Instant createdAt) {
  public enum Type {
    SAVINGS,
    CHECKING
  }
}
