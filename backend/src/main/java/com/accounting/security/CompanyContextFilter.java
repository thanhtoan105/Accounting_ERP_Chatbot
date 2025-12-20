package com.accounting.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.accounting.entity.User;
import com.accounting.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

/**
 * Filter that establishes the company context for each request.
 * This filter runs AFTER JwtAuthenticationFilter in the Spring Security filter chain
 * to ensure the user is authenticated before we try to set the company context.
 */
@Component
public class CompanyContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(CompanyContextFilter.class);

  public static final String COMPANY_HEADER = "X-Company-Id";

  private static final List<String> PUBLIC_PATHS =
      List.of("/api/v1/auth/**", "/api/v1/invitations/**");

  private static final AntPathMatcher pathMatcher = new AntPathMatcher();

  private final UserRepository userRepository;

  public CompanyContextFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String path = request.getRequestURI();

      if (isPublicPath(path)) {
        filterChain.doFilter(request, response);
        return;
      }

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
        filterChain.doFilter(request, response);
        return;
      }

      String header = request.getHeader(COMPANY_HEADER);
      Long headerCompanyId = parseCompanyId(header);

      if (header != null && !header.isBlank() && headerCompanyId == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"error\":{\"code\":\"VALIDATION_ERROR\",\"details\":{\"X-Company-Id\":\"must be a number\"}}}");
        return;
      }

      Long userId;
      try {
        userId = Long.parseLong(auth.getPrincipal().toString());
      } catch (NumberFormatException e) {
        filterChain.doFilter(request, response);
        return;
      }

      User user = userRepository.findById(userId).orElse(null);
      if (user == null) {
        filterChain.doFilter(request, response);
        return;
      }

      if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
        if (headerCompanyId != null) {
          CompanyContext.setCompanyId(headerCompanyId);
        }
        filterChain.doFilter(request, response);
        return;
      }

      Long userCompanyId = user.getCompanyId();
      if (userCompanyId == null) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"error\":{\"code\":\"NO_COMPANY\",\"message\":\"User must belong to a company\"}}");
        return;
      }

      if (headerCompanyId != null && !headerCompanyId.equals(userCompanyId)) {
        log.warn(
            "Company ID mismatch: header={}, user={}, userId={}",
            headerCompanyId,
            userCompanyId,
            userId);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response
            .getWriter()
            .write(
                "{\"error\":{\"code\":\"COMPANY_MISMATCH\",\"message\":\"X-Company-Id does not match user's company\"}}");
        return;
      }

      CompanyContext.setCompanyId(userCompanyId);
      filterChain.doFilter(request, response);
    } finally {
      CompanyContext.clear();
    }
  }

  private boolean isPublicPath(String path) {
    for (String pattern : PUBLIC_PATHS) {
      if (pathMatcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  private Long parseCompanyId(String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(header.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
