package com.example.demo1;

public class UserSession {
    private static String currentUserId;

    public static void setCurrentUserId(String userId) {
        currentUserId = userId;
    }

    public static String getCurrentUserId() {
        return currentUserId;
    }

    public static void clearSession() {
        currentUserId = null;
    }
}