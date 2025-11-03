package com.accounting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CompanyContextFilter extends OncePerRequestFilter {

  public static final String COMPANY_HEADER = "X-Company-Id";

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
      }
      filterChain.doFilter(request, response);
    } finally {
      CompanyContext.clear();
    }
  }
}


