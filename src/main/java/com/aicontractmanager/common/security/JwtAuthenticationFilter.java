package com.aicontractmanager.common.security;

import com.aicontractmanager.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, JdbcTemplate jdbc) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = jwtTokenProvider.parse(authorization.substring(7));
                String userId = claims.getSubject();
                Boolean active = jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id = ? AND deleted_at IS NULL)",
                        Boolean.class, userId);
                if (!Boolean.TRUE.equals(active)) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(
                                userId,
                                null,
                                AuthorityUtils.NO_AUTHORITIES
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
