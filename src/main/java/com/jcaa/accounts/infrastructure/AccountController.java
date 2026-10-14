package com.jcaa.accounts.infrastructure;

import com.jcaa.accounts.application.AccountService;
import com.jcaa.accounts.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  public record CreateAccount(
      @NotBlank @Size(max = 36) String userId, @NotNull Account.Type accountType) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Account create(
      @Valid @RequestBody CreateAccount request,
      @RequestHeader("Authorization") String authorization) {
    return service.create(request.userId(), request.accountType(), authorization);
  }

  @GetMapping("/{id}")
  public Account get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping
  public List<Account> list(@RequestParam @NotBlank String userId) {
    return service.forUser(userId);
  }
}
