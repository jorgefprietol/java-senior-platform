package io.portfolio.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenRulesTest {
  @Test
  void rejectsMissingExpiry() {
    assertTrue(
        TokenRules.validator(ISSUER)
            .validate(token().claims(c -> c.remove("exp")).build())
            .hasErrors());
  }

  private static final String ISSUER = "http://localhost:8180/realms/portfolio";

  private Jwt.Builder token() {
    return Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject("alice")
        .audience(List.of("ledger-api"))
        .issuedAt(Instant.now().minusSeconds(10))
        .expiresAt(Instant.now().plusSeconds(300));
  }

  @Test
  void acceptsExpectedIssuerSubjectAndAudience() {
    assertFalse(TokenRules.validator(ISSUER).validate(token().build()).hasErrors());
  }

  @Test
  void rejectsMissingSubject() {
    assertTrue(
        TokenRules.validator(ISSUER)
            .validate(token().claims(c -> c.remove("sub")).build())
            .hasErrors());
  }

  @Test
  void rejectsBlankSubject() {
    assertTrue(TokenRules.validator(ISSUER).validate(token().subject(" ").build()).hasErrors());
  }

  @Test
  void rejectsWrongSubjectType() {
    assertTrue(TokenRules.validator(ISSUER).validate(token().claim("sub", 42).build()).hasErrors());
  }

  @Test
  void rejectsWrongAudience() {
    assertTrue(
        TokenRules.validator(ISSUER)
            .validate(token().audience(List.of("different-api")).build())
            .hasErrors());
  }

  @Test
  void rejectsDifferentIssuer() {
    assertTrue(
        TokenRules.validator(ISSUER)
            .validate(token().issuer("http://untrusted.example").build())
            .hasErrors());
  }

  @Test
  void rejectsExpiredToken() {
    assertTrue(
        TokenRules.validator(ISSUER)
            .validate(
                token()
                    .issuedAt(Instant.now().minusSeconds(600))
                    .expiresAt(Instant.now().minusSeconds(120))
                    .build())
            .hasErrors());
  }
}
