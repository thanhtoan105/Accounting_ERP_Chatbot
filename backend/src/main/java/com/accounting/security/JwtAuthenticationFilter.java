package com.accounting.security;

import com.accounting.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private final JwtTokenProvider jwtTokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String token = getTokenFromRequest(request);

    if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
      Long userId = jwtTokenProvider.getUserIdFromToken(token);
      String roleStr = jwtTokenProvider.getRoleFromToken(token);

      // Validate role is present and valid
      if (roleStr == null || roleStr.isBlank()) {
        // Missing role claim - authentication fails
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }

      // Convert role string to Role enum and then to Spring Security authority
      Role role = Role.fromString(roleStr);
      if (role == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }

      String authority = role.toAuthority();
      UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
          userId, null, Collections.singletonList(new SimpleGrantedAuthority(authority)));
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }

  private String getTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
