package io.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal monetary rules, without infrastructure dependencies. */
public record Money(BigDecimal value) {
  public Money {
    if (value == null) throw new IllegalArgumentException("Invalid monetary amount");
    value = value.setScale(2, RoundingMode.UNNECESSARY);
    if (value.precision() > 16) throw new IllegalArgumentException("Invalid monetary amount");
  }

  public Money apply(BigDecimal amount) {
    Money delta = new Money(amount);
    if (delta.value.signum() == 0) throw new IllegalArgumentException("Amount must not be zero");
    Money result = new Money(value.add(delta.value));
    if (result.value.signum() < 0) throw new IllegalArgumentException("Insufficient funds");
    return result;
  }
}
