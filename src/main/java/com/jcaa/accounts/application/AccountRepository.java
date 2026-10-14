package com.jcaa.accounts.application;

import com.jcaa.accounts.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
  void save(Account account);

  Optional<Account> find(UUID id);

  List<Account> findByUser(String userId);
}
