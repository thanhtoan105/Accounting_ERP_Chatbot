package com.accounting.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.accounting.security.CompanyContextFilter;
import com.accounting.security.CustomAccessDeniedHandler;
import com.accounting.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final CompanyContextFilter companyContextFilter;
  private final CustomAccessDeniedHandler customAccessDeniedHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CompanyContextFilter companyContextFilter,
      CustomAccessDeniedHandler customAccessDeniedHandler) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.companyContextFilter = companyContextFilter;
    this.customAccessDeniedHandler = customAccessDeniedHandler;
  }

  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("SUPER_ADMIN").implies("ADMIN")
        .role("ADMIN").implies("CHIEF_ACCOUNTANT")
        .role("CHIEF_ACCOUNTANT").implies("CFO", "FINANCE")
        .role("CFO").implies("ACCOUNTANT", "ACCOUNTANT_GENERAL", "ACCOUNTANT_AR", "ACCOUNTANT_AP", "CASHIER")
        .role("FINANCE").implies("ACCOUNTANT", "ACCOUNTANT_GENERAL", "ACCOUNTANT_AR", "ACCOUNTANT_AP", "CASHIER")
        .build();
  }

  @Bean
  public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
    DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
    handler.setRoleHierarchy(roleHierarchy);
    return handler;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        Arrays.asList("Authorization", "Content-Type", "X-Company-Id", "X-Requested-With"));
    configuration.setExposedHeaders(List.of("Authorization"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/invitations/{token}",
                        "/api/v1/invitations/{token}/accept",
                        "/api/v1/invitations/validate/**",
                        "/api/v1/health",
                        "/error")
                    .permitAll()
                    // Super admin only endpoints (tenant management)
                    .requestMatchers("/api/v1/admin/tenants/**")
                    .hasRole("SUPER_ADMIN")
                    // Company admin endpoints (ADMIN and CHIEF_ACCOUNTANT can access)
                    .requestMatchers("/api/v1/admin/company/**", "/api/v1/admin/users/**",
                        "/api/v1/admin/audit-logs/**", "/api/v1/admin/data-integrity/**",
                        "/api/v1/admin/reports/**")
                    .hasAnyRole("ADMIN", "CHIEF_ACCOUNTANT")
                    // All other endpoints require authentication
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(companyContextFilter, JwtAuthenticationFilter.class)
        .exceptionHandling(
            exceptions ->
                exceptions.accessDeniedHandler(customAccessDeniedHandler));

    return http.build();
  }

  /**
   * Disable the automatic servlet filter registration for CompanyContextFilter.
   * We register it manually in the Spring Security filter chain (after JwtAuthenticationFilter),
   * so we don't want it to also run as a standalone servlet filter.
   */
  @Bean
  public FilterRegistrationBean<CompanyContextFilter> companyContextFilterRegistration(
      CompanyContextFilter filter) {
    FilterRegistrationBean<CompanyContextFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
