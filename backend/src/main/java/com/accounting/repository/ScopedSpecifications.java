package com.accounting.repository;

import com.accounting.security.CompanyContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

public final class ScopedSpecifications {

  private ScopedSpecifications() {}

  public static <T extends CompanyScopedEntity> Specification<T> companyScope() {
    return new Specification<T>() {
      @Override
      public Predicate toPredicate(
          Root<T> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        Long companyId = CompanyContext.getCompanyId();
        if (companyId == null) {
          // No scoping applied if not present; caller should enforce for write operations
          return criteriaBuilder.conjunction();
        }
        return criteriaBuilder.equal(root.get("companyId"), companyId);
      }
    };
  }
}


