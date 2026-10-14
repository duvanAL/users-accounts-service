package com.jcaa.accounts.domain;

public class AccountNotFoundException extends RuntimeException {
  public AccountNotFoundException() {
    super("Account not found.");
  }
}
