package com.civicsense.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.civicsense.security.ApiRateLimitFilter;
import com.civicsense.security.JwtAuthenticationFilter;
import com.civicsense.security.RestAccessDeniedHandler;
import com.civicsense.security.RestAuthenticationEntryPoint;

/**
 * Phase 2 security. All rules are enforced in Spring Boot; Supabase Auth is
 * not involved. Health/docs stay public so the Phase 1 pending checks can
 * still be run later without authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiRateLimitFilter apiRateLimitFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            @Value("${civicsense.security.csp:default-src 'self'; script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://tile.openstreetmap.org; "
                    + "connect-src 'self' http://localhost:8080 https://tile.openstreetmap.org; "
                    + "font-src 'self' data:; object-src 'none'; base-uri 'self'; "
                    + "frame-ancestors 'none'; form-action 'self'}") String cspPolicy) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(cspPolicy))
                        .frameOptions(frame -> frame.deny()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiRateLimitFilter, JwtAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/meta/**",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/auth/register/citizen",
                                "/auth/register/department",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/forgot-password",
                                "/auth/reset-password")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/department/**").hasRole("DEPARTMENT_ADMIN")
                        .anyRequest().authenticated());

        return http.build();
    }
}
