package io.portfolio.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuditConsumer {
  private final JdbcTemplate sql;

  public AuditConsumer(JdbcTemplate sql) {
    this.sql = sql;
  }

  @Bean
  Queue auditQueue() {
    return QueueBuilder.durable("ledger.audit.v1")
        .deadLetterExchange("")
        .deadLetterRoutingKey("ledger.audit.dead")
        .build();
  }

  @Bean
  Queue deadQueue() {
    return QueueBuilder.durable("ledger.audit.dead").build();
  }

  @RabbitListener(queues = "ledger.audit.v1")
  @Transactional
  public void receive(String payload) {
    var e = JsonMapper.builder().build().readTree(payload);
    if (e.path("schemaVersion").asInt() != 1 || !e.path("ownerId").isTextual())
      throw new IllegalArgumentException("Unsupported event schema");
    sql.update(
        "insert into audit_events(event_id,account_id,owner_id,amount,balance,sequence,occurred_at) values(?,?,?,?,?,?,?) on conflict(event_id) do nothing",
        UUID.fromString(e.path("eventId").asString()),
        UUID.fromString(e.path("accountId").asString()),
        e.path("ownerId").asString(),
        e.path("amount").decimalValue(),
        e.path("balance").decimalValue(),
        e.path("sequence").asLong(),
        java.sql.Timestamp.from(Instant.parse(e.path("occurredAt").asString())));
    // ACK only follows successful transaction. A replay is safe by event_id uniqueness.
  }
}
