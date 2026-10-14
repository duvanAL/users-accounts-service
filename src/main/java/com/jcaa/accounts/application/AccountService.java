package com.jcaa.accounts.application;

import com.jcaa.accounts.domain.Account;
import com.jcaa.accounts.domain.AccountNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
  private final AccountRepository repository;
  private final UserDirectory directory;
  private final AccountTransaction transaction;

  public AccountService(
      AccountRepository repository, UserDirectory directory, AccountTransaction transaction) {
    this.repository = repository;
    this.directory = directory;
    this.transaction = transaction;
  }

  public Account create(String userId, Account.Type type, String authorization) {
    UserDirectory.UserContact contact = directory.get(userId, authorization);
    return transaction.create(contact, type);
  }

  public Account get(UUID id) {
    return repository.find(id).orElseThrow(AccountNotFoundException::new);
  }

  public List<Account> forUser(String userId) {
    return repository.findByUser(userId);
  }
}
