package com.jcaa.accounts.infrastructure;

import com.jcaa.accounts.application.AccountService;
import com.jcaa.accounts.domain.Account;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonalAccountsController {
  private final AccountService accounts;
  public PersonalAccountsController(AccountService accounts) { this.accounts = accounts; }
  @GetMapping("/api/me/accounts")
  public List<Account> own(Authentication authentication) {
    return accounts.forUser(authentication.getName());
  }
}
