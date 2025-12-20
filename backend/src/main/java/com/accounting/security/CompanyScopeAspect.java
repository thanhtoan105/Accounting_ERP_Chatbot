package com.accounting.security;

import java.util.Collection;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.accounting.repository.CompanyScopedEntity;

@Aspect
@Component
public class CompanyScopeAspect {

  @Before(
      "execution(* org.springframework.data.jpa.repository.JpaRepository.save(..)) || "
          + "execution(* org.springframework.data.jpa.repository.JpaRepository.saveAll(..))")
  public void beforeSave(JoinPoint joinPoint) {
    Object[] args = joinPoint.getArgs();
    if (args == null || args.length == 0) {
      return;
    }
    Object arg = args[0];
    if (arg instanceof CompanyScopedEntity scoped) {
      CompanyScopeEnforcer.enforceForWrite(scoped);
    } else if (arg instanceof Collection<?> coll) {
      for (Object item : coll) {
        if (item instanceof CompanyScopedEntity scopedItem) {
          CompanyScopeEnforcer.enforceForWrite(scopedItem);
        }
      }
    }
  }
}
