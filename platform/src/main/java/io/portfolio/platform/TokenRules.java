package io.portfolio.platform;

import java.util.List;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

public final class TokenRules {
  private TokenRules() {}

  public static OAuth2TokenValidator<Jwt> validator(String issuer) {
    OAuth2TokenValidator<Jwt> subjectAndAudience =
        jwt -> {
          Object subject = jwt.getClaims().get("sub");
          Object audience = jwt.getClaims().get("aud");
          if (subject instanceof String value
              && !value.isBlank()
              && value.length() <= 100
              && jwt.getClaims().containsKey("exp")
              && audience instanceof List<?> values
              && values.contains("ledger-api")) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error("invalid_token", "Missing or invalid subject/audience", null));
        };
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer), subjectAndAudience);
  }
}
