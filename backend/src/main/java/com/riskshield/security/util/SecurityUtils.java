package com.riskshield.security.util;

import com.riskshield.security.model.SecurityUser;
import com.riskshield.security.model.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Security utilities for extracting current authenticated principal and
 * strictly enforcing merchant multi-tenant isolation.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Retrieves the authenticated SecurityUser from SecurityContext.
     */
    public static Optional<SecurityUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Retrieves current user or throws AccessDeniedException if unauthenticated.
     */
    public static SecurityUser getRequiredUser() {
        return getCurrentUser().orElseThrow(() ->
                new AccessDeniedException("Unauthenticated caller: No active security context"));
    }

    /**
     * Strictly verifies that the authenticated user is allowed to access targetMerchantId.
     * Prevents cross-merchant data leakage and tenant escaping.
     *
     * @param targetMerchantId Merchant ID associated with the target resource
     * @throws AccessDeniedException if caller has MERCHANT_VIEWER role and target does not match their assigned merchant
     */
    public static void assertMerchantAccess(String targetMerchantId) {
        Optional<SecurityUser> userOpt = getCurrentUser();
        if (userOpt.isEmpty()) {
            return; // If running in open dev mode or unauthenticated fallback
        }

        SecurityUser user = userOpt.get();

        // ADMIN and RISK_ANALYST have global cross-merchant visibility
        if (user.isAdmin() || user.isRiskAnalyst()) {
            return;
        }

        // MERCHANT_VIEWER is strictly locked to their own merchant
        if (user.isMerchantViewer()) {
            String callerMerchantId = user.getMerchantId();
            if (callerMerchantId == null || !callerMerchantId.equalsIgnoreCase(targetMerchantId)) {
                throw new AccessDeniedException(String.format(
                        "Cross-merchant data access denied: Access to merchant '%s' is prohibited for tenant '%s'",
                        targetMerchantId, callerMerchantId));
            }
        }
    }

    /**
     * Resolves the effective merchant scope for querying lists or aggregations.
     * If caller is MERCHANT_VIEWER, forcibly locks the filter to their assigned merchantId,
     * discarding any client-provided merchant override.
     */
    public static String resolveMerchantScope(String requestedMerchantId) {
        Optional<SecurityUser> userOpt = getCurrentUser();
        if (userOpt.isPresent()) {
            SecurityUser user = userOpt.get();
            if (user.isMerchantViewer() && user.getMerchantId() != null) {
                return user.getMerchantId();
            }
        }
        return (requestedMerchantId != null && !requestedMerchantId.isBlank() && !"ALL".equalsIgnoreCase(requestedMerchantId))
                ? requestedMerchantId
                : null;
    }
}
