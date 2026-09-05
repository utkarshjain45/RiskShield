package com.riskshield.security.filter;

import com.riskshield.security.model.SecurityUser;
import com.riskshield.security.model.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter that authenticates incoming requests using API Keys or secure role headers.
 * Populates SecurityContext with SecurityUser principal containing role and merchant tenancy.
 */
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_AUTH = "Authorization";
    public static final String HEADER_ROLE = "X-User-Role";
    public static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    public static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Extract key from X-API-Key or Bearer header
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            String authHeader = request.getHeader(HEADER_AUTH);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                apiKey = authHeader.substring(7).trim();
            }
        }

        String roleHeader = request.getHeader(HEADER_ROLE);
        String merchantIdHeader = request.getHeader(HEADER_MERCHANT_ID);
        String userIdHeader = request.getHeader(HEADER_USER_ID);

        SecurityUser securityUser = null;

        // 1. Resolve from explicit API key
        if (apiKey != null && !apiKey.isBlank()) {
            securityUser = resolveUserFromApiKey(apiKey, userIdHeader);
        }
        // 2. Resolve from Role Header (Used by inter-service clients and automated tests)
        else if (roleHeader != null && !roleHeader.isBlank()) {
            securityUser = resolveUserFromRoleHeader(roleHeader, merchantIdHeader, userIdHeader);
        }

        if (securityUser != null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    securityUser,
                    null,
                    securityUser.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private SecurityUser resolveUserFromApiKey(String apiKey, String userId) {
        String effectiveUserId = userId != null && !userId.isBlank() ? userId : "api_client";

        // Admin Key Convention: starts with adm_
        if (apiKey.startsWith("adm_") || apiKey.contains("admin")) {
            return SecurityUser.builder()
                    .userId(effectiveUserId)
                    .username("Administrator")
                    .roles(Set.of(UserRole.ADMIN, UserRole.RISK_ANALYST))
                    .merchantId(null)
                    .build();
        }

        // Analyst Key Convention: starts with ana_
        if (apiKey.startsWith("ana_") || apiKey.contains("analyst")) {
            return SecurityUser.builder()
                    .userId(effectiveUserId)
                    .username("RiskAnalyst")
                    .roles(Set.of(UserRole.RISK_ANALYST))
                    .merchantId(null)
                    .build();
        }

        // Merchant Key Convention: mch_<merchantId>_...
        if (apiKey.startsWith("mch_")) {
            String[] parts = apiKey.split("_");
            String merchantId = parts.length >= 2 ? parts[1] : "mer_default";
            return SecurityUser.builder()
                    .userId(effectiveUserId)
                    .username("MerchantViewer-" + merchantId)
                    .roles(Set.of(UserRole.MERCHANT_VIEWER))
                    .merchantId(merchantId)
                    .build();
        }

        // Default authenticated user
        return SecurityUser.builder()
                .userId(effectiveUserId)
                .username("Client-" + effectiveUserId)
                .roles(Set.of(UserRole.RISK_ANALYST))
                .merchantId(null)
                .build();
    }

    private SecurityUser resolveUserFromRoleHeader(String roleStr, String merchantId, String userId) {
        String effectiveUserId = userId != null && !userId.isBlank() ? userId : "user_header";
        Set<UserRole> roles = new HashSet<>();

        try {
            UserRole role = UserRole.valueOf(roleStr.trim().toUpperCase());
            roles.add(role);
            if (role == UserRole.ADMIN) {
                roles.add(UserRole.RISK_ANALYST);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-User-Role header provided: {}", roleStr);
            return null;
        }

        return SecurityUser.builder()
                .userId(effectiveUserId)
                .username(effectiveUserId)
                .merchantId(merchantId != null && !merchantId.isBlank() ? merchantId.trim() : null)
                .roles(roles)
                .build();
    }
}
