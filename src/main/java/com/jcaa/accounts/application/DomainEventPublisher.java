package com.jcaa.accounts.application;

import com.jcaa.events.DomainEvent;

public interface DomainEventPublisher {
  void enqueue(DomainEvent event);
}
