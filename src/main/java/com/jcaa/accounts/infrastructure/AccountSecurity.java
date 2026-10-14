package com.jcaa.accounts.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class AccountSecurity {
  @Bean
  SecurityFilterChain accountSecurityFilterChain(
      HttpSecurity http, @Value("${JWT_SECRET}") String secret) throws Exception {
    SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    OncePerRequestFilter tokens =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
              try {
                Claims claims =
                    Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(header.substring(7))
                        .getPayload();
                String role = claims.get("role", String.class);
                if (claims.getExpiration() != null
                    && claims.getSubject() != null
                    && List.of("ADMIN", "REVIEWER", "MEMBER").contains(role)) {
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              claims.getSubject(),
                              null,
                              List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                }
              } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
              }
            }
            chain.doFilter(request, response);
          }
        };
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json");
                      res.getWriter()
                          .write("{\"status\":401,\"message\":\"Authentication required.\"}");
                    }))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/health", "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/accounts")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/accounts", "/api/accounts/**")
                    .hasAnyRole("ADMIN", "REVIEWER")
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(tokens, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
