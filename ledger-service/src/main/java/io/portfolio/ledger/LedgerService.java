package io.portfolio.ledger;

import io.portfolio.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LedgerService {
  private final AccountRepository accounts;
  private final JdbcTemplate sql;

  public LedgerService(AccountRepository accounts, JdbcTemplate sql) {
    this.accounts = accounts;
    this.sql = sql;
  }

  public record Movement(
      UUID id,
      UUID accountId,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal amount,
      @com.fasterxml.jackson.annotation.JsonFormat(
              shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
          BigDecimal balance,
      String description,
      Instant occurredAt,
      long sequence) {}

  public Account owned(UUID id, String owner) {
    Account a =
        accounts
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    if (!a.ownerId.equals(owner))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    return a;
  }

  @Transactional
  public Movement post(
      UUID accountId, String owner, UUID key, BigDecimal amount, String description) {
    // Serialize identical request keys across replicas inside the transaction.
    sql.queryForList(
        "select pg_advisory_xact_lock(?)",
        key.getMostSignificantBits() ^ key.getLeastSignificantBits());
    Account a = owned(accountId, owner);
    var existing =
        sql.query(
            "select * from movements where id=?",
            (rs, n) ->
                new Movement(
                    rs.getObject("id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getBigDecimal("amount"),
                    rs.getBigDecimal("balance"),
                    rs.getString("description"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getLong("sequence")),
            key);
    if (!existing.isEmpty()) {
      Movement old = existing.getFirst();
      if (!old.accountId.equals(accountId)
          || old.amount.compareTo(amount) != 0
          || !old.description.equals(description))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Idempotency key already used for a different request");
      return old;
    }
    a.balance = new Money(a.balance).apply(amount).value();
    accounts.saveAndFlush(
        a); // @Version prevents lost updates and rolls back the entire transaction on conflict.
    // PostgreSQL stores microseconds. Normalize before responding so a retry returns identical
    // data.
    Instant occurredAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    Movement m = new Movement(key, a.id, amount, a.balance, description, occurredAt, a.version);
    sql.update(
        "insert into movements(id,account_id,owner_id,amount,balance,description,occurred_at,sequence) values(?,?,?,?,?,?,?,?)",
        key,
        a.id,
        owner,
        amount,
        a.balance,
        description,
        java.sql.Timestamp.from(m.occurredAt),
        a.version);
    String payload =
        JsonMapper.builder()
            .build()
            .writeValueAsString(
                Map.of(
                    "schemaVersion",
                    1,
                    "eventId",
                    key.toString(),
                    "accountId",
                    a.id.toString(),
                    "ownerId",
                    owner,
                    "amount",
                    amount,
                    "balance",
                    a.balance,
                    "occurredAt",
                    m.occurredAt.toString(),
                    "sequence",
                    a.version));
    sql.update("insert into outbox(id,payload) values(?,?)", key, payload);
    return m;
  }

  public List<Movement> history(UUID id, String owner, long before) {
    owned(id, owner);
    return sql.query(
        "select * from movements where account_id=? and sequence<? order by sequence desc limit 50",
        (rs, n) ->
            new Movement(
                rs.getObject("id", UUID.class),
                id,
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance"),
                rs.getString("description"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getLong("sequence")),
        id,
        before);
  }
}
