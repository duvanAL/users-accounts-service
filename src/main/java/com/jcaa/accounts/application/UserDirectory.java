package com.jcaa.accounts.application;

public interface UserDirectory {
  UserContact get(String userId, String authorization);

  record UserContact(String id, String name, String email) {}
}
