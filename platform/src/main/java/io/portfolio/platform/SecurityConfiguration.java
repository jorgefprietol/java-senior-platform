package io.portfolio.platform;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
  @Bean
  org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(
      @org.springframework.beans.factory.annotation.Value(
              "${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
          String issuer,
      @org.springframework.beans.factory.annotation.Value(
              "${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
          String jwks) {
    var decoder =
        org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(TokenRules.validator(issuer));
    return decoder;
  }

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Map<String, Object> realm = jwt.getClaim("realm_access");
          Object roles = realm == null ? List.of() : realm.get("roles");
          if (!(roles instanceof Collection<?> values)) return List.of();
          return values.stream()
              .map(
                  r ->
                      (org.springframework.security.core.GrantedAuthority)
                          new SimpleGrantedAuthority("ROLE_" + r))
              .toList();
        });
    // API authenticates only explicit bearer headers, never ambient browser cookies.
    return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(c -> c.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasRole("operator")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .build();
  }
}
