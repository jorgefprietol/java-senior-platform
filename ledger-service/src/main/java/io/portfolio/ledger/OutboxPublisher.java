package io.portfolio.ledger;

import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
  private final JdbcTemplate sql;
  private final RabbitTemplate rabbit;

  public OutboxPublisher(JdbcTemplate sql, RabbitTemplate rabbit) {
    this.sql = sql;
    this.rabbit = rabbit;
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

  @Scheduled(fixedDelay = 1000)
  @Transactional
  public void publish() {
    var rows =
        sql.queryForList(
            "select id,payload from outbox where published_at is null order by created_at limit 20 for update skip locked");
    for (var row : rows) {
      try {
        CorrelationData correlation = new CorrelationData(row.get("id").toString());
        rabbit.convertAndSend(
            "",
            "ledger.audit.v1",
            row.get("payload"),
            m -> {
              m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
              return m;
            },
            correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null)
          throw new IllegalStateException("Broker did not route and confirm event");
        sql.update("update outbox set published_at=now() where id=?", row.get("id"));
      } catch (Exception ex) {
        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        LoggerFactory.getLogger(getClass())
            .warn("Outbox delivery deferred: {}", ex.getClass().getSimpleName());
        break;
      }
    }
  }
}
