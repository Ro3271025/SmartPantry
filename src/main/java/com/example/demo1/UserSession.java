package com.example.demo1;

public class UserSession {
    private static String currentUserId;
    private static String currentUserName;

    public static void setCurrentUserId(String userId) {
        currentUserId = userId;
    }

    public static String getCurrentUserId() {
        return currentUserId;
    }

    public static void setCurrentUserName(String userName) {
        currentUserName = userName;
    }
    public static String getCurrentUserName() {
        return currentUserName;
    }

    public static void clearSession() {
        currentUserId = null;
        currentUserName = null;
    }

}