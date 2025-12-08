package com.accounting.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.Invitation;

/**
 * Repository for Invitation entity.
 */
public interface InvitationRepository
    extends JpaRepository<Invitation, Long>, JpaSpecificationExecutor<Invitation> {

  /**
   * Find invitation by token.
   *
   * @param token invitation token
   * @return invitation or empty
   */
  Optional<Invitation> findByToken(String token);

  /**
   * Find invitation by email and company ID.
   *
   * @param email user email
   * @param companyId company ID
   * @return invitation or empty
   */
  Optional<Invitation> findByEmailAndCompanyId(String email, Long companyId);

  /**
   * Find all expired pending invitations.
   *
   * @param now current timestamp
   * @return list of expired pending invitations
   */
  @Query("SELECT i FROM Invitation i WHERE i.status = 'PENDING' AND i.expiresAt < :now")
  List<Invitation> findExpiredPending(@Param("now") Instant now);
}
