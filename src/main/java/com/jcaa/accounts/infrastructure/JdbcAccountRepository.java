package com.jcaa.accounts.infrastructure;

import com.jcaa.accounts.application.AccountRepository;
import com.jcaa.accounts.domain.Account;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAccountRepository implements AccountRepository {
  private final JdbcTemplate jdbc;

  public JdbcAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(Account a) {
    jdbc.update(
        "INSERT INTO accounts.accounts(id, user_id, account_type, created_at) VALUES (?, ?, ?, ?)",
        a.id(),
        a.userId(),
        a.accountType().name(),
        Timestamp.from(a.createdAt()));
  }

  public Optional<Account> find(UUID id) {
    return jdbc
        .query("SELECT * FROM accounts.accounts WHERE id = ?", JdbcAccountRepository::map, id)
        .stream()
        .findFirst();
  }

  public List<Account> findByUser(String id) {
    return jdbc.query(
        "SELECT * FROM accounts.accounts WHERE user_id = ? ORDER BY created_at DESC LIMIT 100",
        JdbcAccountRepository::map,
        id);
  }

  private static Account map(ResultSet r, int index) throws SQLException {
    return new Account(
        UUID.fromString(r.getString("id")),
        r.getString("user_id"),
        Account.Type.valueOf(r.getString("account_type")),
        r.getTimestamp("created_at").toInstant());
  }
}
