package io.portfolio.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
public class AccountsController {
  private final AccountRepository accounts;
  private final LedgerService ledger;

  public AccountsController(AccountRepository accounts, LedgerService ledger) {
    this.accounts = accounts;
    this.ledger = ledger;
  }

  public record CreateAccount(
      @NotBlank @Size(max = 80) String name,
      @NotNull @Pattern(regexp = "USD|EUR") String currency) {}

  public record PostMovement(
      @NotNull @Digits(integer = 14, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 140) String description) {}

  public record AccountView(UUID id, String name, String currency, String balance, long version) {
    static AccountView from(Account a) {
      return new AccountView(a.id, a.name, a.currency, a.balance.toPlainString(), a.version);
    }
  }

  @GetMapping
  public List<AccountView> list(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
    if (page < 0 || page > 1000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
    return accounts
        .findByOwnerIdOrderByCreatedAtDesc(jwt.getSubject(), PageRequest.of(page, 25))
        .stream()
        .map(AccountView::from)
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountView create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccount body) {
    return AccountView.from(
        accounts.save(new Account(jwt.getSubject(), body.name.strip(), body.currency)));
  }

  @GetMapping("/{id}")
  public AccountView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return AccountView.from(ledger.owned(id, jwt.getSubject()));
  }

  @GetMapping("/{id}/movements")
  public List<LedgerService.Movement> movements(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "9223372036854775807") long before) {
    return ledger.history(id, jwt.getSubject(), before);
  }

  @PostMapping("/{id}/movements")
  @ResponseStatus(HttpStatus.CREATED)
  public LedgerService.Movement post(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody PostMovement body) {
    try {
      return ledger.post(id, jwt.getSubject(), key, body.amount, body.description.strip());
    } catch (OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Concurrent update; retry with the same Idempotency-Key");
    }
  }
}
