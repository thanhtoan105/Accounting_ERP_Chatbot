package com.accounting.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.accounting.entity.User;
import com.accounting.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CompanyContextFilter extends OncePerRequestFilter {

  public static final String COMPANY_HEADER = "X-Company-Id";

  private final UserRepository userRepository;

  public CompanyContextFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String header = request.getHeader(COMPANY_HEADER);
      if (header != null && !header.isBlank()) {
        try {
          Long companyId = Long.parseLong(header.trim());
          CompanyContext.setCompanyId(companyId);
        } catch (NumberFormatException ex) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          response.setContentType("application/json");
          response.getWriter()
              .write(
                  "{\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":{\"X-Company-Id\":\"must be a number\"}}}");
          return;
        }
      } else {
        // Fallback: derive company from authenticated user if header missing
        try {
          Authentication auth = SecurityContextHolder.getContext().getAuthentication();
          if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            Long currentUserId = Long.parseLong(auth.getPrincipal().toString());
            userRepository.findById(currentUserId).ifPresent((User u) -> {
              if (u.getCompanyId() != null) {
                CompanyContext.setCompanyId(u.getCompanyId());
              }
            });
          }
        } catch (Exception ignored) {
          // best-effort fallback; continue without blocking
        }
      }
      filterChain.doFilter(request, response);
    } finally {
      CompanyContext.clear();
    }
  }
}
