package io.portfolio.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
  @Id public UUID id;

  @Column(name = "owner_id", nullable = false, length = 100)
  public String ownerId;

  @Column(nullable = false, length = 80)
  public String name;

  @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  public String currency;

  @Column(nullable = false, precision = 16, scale = 2)
  public BigDecimal balance;

  @Version public long version;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  protected Account() {}

  public Account(String owner, String name, String currency) {
    id = UUID.randomUUID();
    ownerId = owner;
    this.name = name;
    this.currency = currency;
    balance = new BigDecimal("0.00");
    createdAt = Instant.now();
  }
}
