package io.portfolio.audit;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
  private final JdbcTemplate sql;

  public AuditController(JdbcTemplate sql) {
    this.sql = sql;
  }

  @GetMapping
  public List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
    return sql.queryForList(
        "select event_id,account_id,amount::text as amount,balance::text as balance,sequence,occurred_at from audit_events where owner_id=? order by occurred_at desc,event_id limit 100",
        jwt.getSubject());
  }
}
