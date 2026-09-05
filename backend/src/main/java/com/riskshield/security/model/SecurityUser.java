package com.riskshield.security.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Principal representing an authenticated user or service client in RiskShield AI.
 * Encapsulates tenant scoping via merchantId.
 */
@Getter
@Builder
public class SecurityUser implements UserDetails {

    private final String userId;
    private final String username;
    private final String merchantId; // Null for ADMIN / global RISK_ANALYST; non-null for MERCHANT_VIEWER
    private final Set<UserRole> roles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority(r.authority()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return ""; // Header/API-key based authentication; no stored credential in principal
    }

    @Override
    public String getUsername() {
        return username != null ? username : userId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean hasRole(UserRole role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    public boolean isRiskAnalyst() {
        return hasRole(UserRole.RISK_ANALYST);
    }

    public boolean isMerchantViewer() {
        return hasRole(UserRole.MERCHANT_VIEWER);
    }
}
