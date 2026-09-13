package io.portfolio.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void decimalArithmeticIsExact() {
    assertEquals(
        new BigDecimal("0.30"),
        new Money(new BigDecimal("0.10")).apply(new BigDecimal("0.20")).value());
  }

  @Test
  void withdrawalCannotOverdraw() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Money(new BigDecimal("20")).apply(new BigDecimal("-21")));
  }

  @Test
  void withdrawalMayEmptyAccount() {
    assertEquals(
        new BigDecimal("0.00"),
        new Money(new BigDecimal("20")).apply(new BigDecimal("-20")).value());
  }

  @Test
  void rejectsHiddenRounding() {
    assertThrows(ArithmeticException.class, () -> new Money(new BigDecimal("1.001")));
  }

  @Test
  void rejectsZeroMovement() {
    assertThrows(
        IllegalArgumentException.class, () -> new Money(BigDecimal.TEN).apply(BigDecimal.ZERO));
  }

  @Test
  void rejectsOverflow() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Money(new BigDecimal("99999999999999.99")).apply(BigDecimal.ONE));
  }
}
