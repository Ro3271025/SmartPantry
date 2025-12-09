package com.example.demo1;

import javafx.scene.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages application-wide theme switching between light and dark modes.
 * Persists user's theme preference and applies it to all scenes.
 */
public class ThemeManager {
    private static ThemeManager instance;
    private static final String THEME_PREF_KEY = "app_theme";
    private static final String LIGHT_THEME = "light";
    private static final String DARK_THEME = "dark";

    private final Preferences prefs;
    private String currentTheme;
    private final List<Scene> registeredScenes;

    private ThemeManager() {
        this.prefs = Preferences.userNodeForPackage(ThemeManager.class);
        this.registeredScenes = new ArrayList<>();
        // Load saved theme preference, default to light
        this.currentTheme = prefs.get(THEME_PREF_KEY, LIGHT_THEME);
    }

    public static ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * Register a scene to have themes applied to it
     */
    public void registerScene(Scene scene) {
        if (scene != null && !registeredScenes.contains(scene)) {
            registeredScenes.add(scene);
            applyThemeToScene(scene);
        }
    }

    /**
     * Unregister a scene (cleanup when scene is closed)
     */
    public void unregisterScene(Scene scene) {
        registeredScenes.remove(scene);
    }

    /**
     * Toggle between light and dark themes
     */
    public void toggleTheme() {
        if (isDarkMode()) {
            setLightTheme();
        } else {
            setDarkTheme();
        }
    }

    /**
     * Set light theme
     */
    public void setLightTheme() {
        currentTheme = LIGHT_THEME;
        prefs.put(THEME_PREF_KEY, LIGHT_THEME);
        applyThemeToAllScenes();
    }

    /**
     * Set dark theme
     */
    public void setDarkTheme() {
        currentTheme = DARK_THEME;
        prefs.put(THEME_PREF_KEY, DARK_THEME);
        applyThemeToAllScenes();
    }

    /**
     * Check if dark mode is currently active
     */
    public boolean isDarkMode() {
        return DARK_THEME.equals(currentTheme);
    }

    /**
     * Get current theme name
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Apply current theme to all registered scenes
     */
    private void applyThemeToAllScenes() {
        for (Scene scene : new ArrayList<>(registeredScenes)) {
            applyThemeToScene(scene);
        }
    }

    /**
     * Apply current theme to a specific scene
     */
    private void applyThemeToScene(Scene scene) {
        if (scene == null) return;

        scene.getStylesheets().clear();

        // Add base stylesheets that every scene needs
        String basePath = "/com/example/demo1/";

        if (isDarkMode()) {
            // Dark theme stylesheets (order matters - later ones override earlier)
            addStylesheetIfExists(scene, basePath + "dark-theme.css");
            addStylesheetIfExists(scene, basePath + "pantry-dark.css");
            addStylesheetIfExists(scene, basePath + "Recipe-dark.css");
            addStylesheetIfExists(scene, basePath + "styles-dark.css");
            addStylesheetIfExists(scene, basePath + "addItem-dark.css");
            addStylesheetIfExists(scene, basePath + "ShoppingList-dark.css"); // Must be last for Shopping List
        } else {
            // Light theme stylesheets
            addStylesheetIfExists(scene, basePath + "style.css");
            addStylesheetIfExists(scene, basePath + "pantry.css");
            addStylesheetIfExists(scene, basePath + "Recipe.css");
            addStylesheetIfExists(scene, basePath + "styles.css");
            addStylesheetIfExists(scene, basePath + "addItem.css");
            addStylesheetIfExists(scene, basePath + "ShoppingList.css");
        }
    }

    /**
     * Add stylesheet to scene if it exists
     */
    private void addStylesheetIfExists(Scene scene, String path) {
        try {
            String stylesheet = getClass().getResource(path).toExternalForm();
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet);
            }
        } catch (Exception e) {
            // Stylesheet doesn't exist, skip it
            System.out.println("Stylesheet not found: " + path);
        }
    }

    /**
     * Get the emoji icon for the current theme
     */
    public String getThemeIcon() {
        return isDarkMode() ? "☀️" : "🌙";
    }

    /**
     * Get the tooltip text for the theme button
     */
    public String getThemeTooltip() {
        return isDarkMode() ? "Switch to Light Mode" : "Switch to Dark Mode";
    }
}