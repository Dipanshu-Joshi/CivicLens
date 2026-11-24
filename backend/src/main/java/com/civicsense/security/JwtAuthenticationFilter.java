package com.civicsense.security;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.civicsense.domain.AccountStatus;
import com.civicsense.identity.User;
import com.civicsense.identity.UserRepository;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Converts a valid Bearer access token into a SecurityContext principal.
 * User account status and department verification are re-read from the
 * database on every request so suspension/verification take effect immediately.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PrincipalFactory principalFactory;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            PrincipalFactory principalFactory) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.principalFactory = principalFactory;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = resolveBearerToken(request);
        if (token != null) {
            try {
                Claims claims = jwtService.parse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                Optional<User> user = userRepository.findById(userId);
                if (user.isPresent() && user.get().getAccountStatus() == AccountStatus.ACTIVE) {
                    AuthenticatedUser principal = principalFactory.from(user.get());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.authorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ex) {
                // Invalid/expired tokens are reported by the entry point as 401.
                SecurityContextHolder.clearContext();
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

}
