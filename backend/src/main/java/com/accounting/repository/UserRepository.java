package com.accounting.repository;

import com.accounting.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
    extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  // Find by role and company ID
  List<User> findByRoleAndCompanyId(String role, Long companyId);

  // Find by status and company ID
  List<User> findByStatusAndCompanyId(String status, Long companyId);

  // Find by email containing (case-insensitive) and company ID
  List<User> findByEmailContainingIgnoreCaseAndCompanyId(String email, Long companyId);

  // Find by full name containing (case-insensitive) and company ID
  List<User> findByFullNameContainingIgnoreCaseAndCompanyId(String fullName, Long companyId);

  // Find by role, status, and company ID
  List<User> findByRoleAndStatusAndCompanyId(String role, String status, Long companyId);

  // Find by email or full name containing (case-insensitive) and company ID
  @Query(
      "SELECT u FROM User u WHERE u.companyId = :companyId "
          + "AND (LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) "
          + "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')))")
  List<User> findByEmailOrFullNameContainingIgnoreCaseAndCompanyId(
      @Param("search") String search, @Param("companyId") Long companyId);

  // Find by reset token (for password reset)
  @Query(
      "SELECT u FROM User u WHERE u.resetToken = :token "
          + "AND u.resetTokenExpiry IS NOT NULL "
          + "AND u.resetTokenExpiry > :now")
  Optional<User> findByResetTokenAndExpiryAfter(
      @Param("token") String token, @Param("now") java.time.Instant now);
}

