package com.riskshield.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskshield.security.filter.ApiKeyAuthenticationFilter;
import com.riskshield.security.filter.ApiRateLimitingFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Strict CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Stateless REST CSRF policy
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Stateless Session Management
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Custom Error Handlers for 401 and 403
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                                    "success", false,
                                    "status", "UNAUTHORIZED",
                                    "message", "Authentication required. Provide valid X-API-Key or Authorization header.",
                                    "timestamp", Instant.now().toString()
                            )));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                                    "success", false,
                                    "status", "FORBIDDEN",
                                    "message", "Access denied: " + accessDeniedException.getMessage(),
                                    "timestamp", Instant.now().toString()
                            )));
                        })
                )

                // 5. Authorization Rules
                .authorizeHttpRequests(auth -> auth
                        // Public probes and docs
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Razorpay webhooks: public HTTP ingestion point secured via HMAC-SHA256 signature verification
                        .requestMatchers("/api/v1/webhooks/razorpay", "/api/v1/webhooks/razorpay/**").permitAll()

                        // Policy mutation requires ADMIN or RISK_ANALYST
                        .requestMatchers(HttpMethod.POST, "/api/v1/policies/**").hasAnyRole("ADMIN", "RISK_ANALYST")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/policies/**").hasAnyRole("ADMIN", "RISK_ANALYST")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/policies/**").hasRole("ADMIN")

                        // Incident resolution requires ADMIN or RISK_ANALYST
                        .requestMatchers("/api/v1/incidents/*/acknowledge", "/api/v1/incidents/*/resolve").hasAnyRole("ADMIN", "RISK_ANALYST")

                        // Model evaluation held-out test data restricted to internal staff (ADMIN, RISK_ANALYST)
                        .requestMatchers("/api/v1/model/evaluation/**").hasAnyRole("ADMIN", "RISK_ANALYST")

                        // Actuator administrative metrics require ADMIN
                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        // All other API endpoints require authenticated access
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll()
                )

                // 6. Register Rate Limiter & Authentication Filters
                .addFilterBefore(new ApiRateLimitingFilter(objectMapper), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiKeyAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:8080",
                "http://127.0.0.1:8080"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-API-Key",
                "X-User-Role",
                "X-Merchant-Id",
                "X-User-Id",
                "X-Razorpay-Signature",
                "X-Razorpay-Event-Id",
                "X-Correlation-ID"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-ID", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
