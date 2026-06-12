package com.lxy.flowplan.context;

import io.jsonwebtoken.Claims;

public class AppUserContext {
    private AppUserContext() {}

    private static final ThreadLocal<Claims> HOLDER = new ThreadLocal<>();

    public static void set(Claims claims) { HOLDER.set(claims); }

    public static Claims get() { return HOLDER.get(); }

    public static String getUsername() {
        Claims claims = get();
        return claims == null ? null : claims.getSubject();
    }

    public static Integer getUserId() {
        Claims claims = get();
        return claims == null ? null : claims.get("id", Integer.class);
    }

    public static String getRole() {
        Claims claims = get();
        return claims == null ? null : claims.get("role", String.class);
    }

    public static void remove() { HOLDER.remove(); }
}
