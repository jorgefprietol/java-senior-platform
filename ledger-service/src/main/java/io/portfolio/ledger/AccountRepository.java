package io.portfolio.ledger;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Page<Account> findByOwnerIdOrderByCreatedAtDesc(String owner, Pageable page);
}
