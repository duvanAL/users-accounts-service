package com.jcaa.accounts.application;

import com.jcaa.accounts.domain.Account;
import com.jcaa.events.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTransaction {
  private final AccountRepository repository;
  private final DomainEventPublisher outbox;

  public AccountTransaction(AccountRepository repository, DomainEventPublisher outbox) {
    this.repository = repository;
    this.outbox = outbox;
  }

  @Transactional
  public Account create(UserDirectory.UserContact user, Account.Type type) {
    Account account = new Account(UUID.randomUUID(), user.id(), type, Instant.now());
    repository.save(account);
    outbox.enqueue(
        DomainEvent.create(
            "account.created",
            Map.of(
                "accountId",
                account.id().toString(),
                "userId",
                user.id(),
                "name",
                user.name(),
                "email",
                user.email(),
                "accountType",
                type.name())));
    return account;
  }
}
