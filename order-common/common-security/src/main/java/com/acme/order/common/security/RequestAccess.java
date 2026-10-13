package com.acme.order.common.security;

/** Shared exact-route access policy for gateway and servlet services. */
public final class RequestAccess {
    private RequestAccess() { }

    /** Reject alternate spellings that a downstream router could normalize differently. */
    public static boolean isSafePath(String rawPath) {
        return rawPath != null && rawPath.startsWith("/") && !rawPath.contains(";")
            && !rawPath.contains("%") && !rawPath.contains("\\") && !rawPath.contains("//")
            && !rawPath.matches(".*(?:^|/)\\.{1,2}(?:/|$).*");
    }

    public static boolean isPublic(String method, String path) {
        return ("POST".equals(method) && ("/api/auth/login".equals(path) || "/api/auth/register".equals(path)))
            || ("GET".equals(method) && ("/api/products".equals(path) || path.matches("/api/skus/[0-9]+")))
            || ("GET".equals(method) && ("/actuator/health".equals(path) || path.startsWith("/actuator/health/")
                || "/actuator/prometheus".equals(path)));
    }

    public static boolean isAdmin(String path) {
        return path.equals("/api/admin") || path.startsWith("/api/admin/")
            || path.equals("/admin") || path.startsWith("/admin/") || path.startsWith("/actuator/");
    }

    public static boolean isInternal(String path) {
        return path.equals("/internal") || path.startsWith("/internal/");
    }
}
