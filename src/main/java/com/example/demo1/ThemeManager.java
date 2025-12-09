package com.example.demo1;

import javafx.scene.Scene;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages application-wide theme switching between light and dark modes.
 * Handles the split CSS file structure:
 *   - /com/example/demo1/  (primary)
 *   - /CSSFiles/           (secondary)
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
        System.out.println("ThemeManager initialized. Current theme: " + currentTheme);
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
            System.out.println("✓ Registered scene with ThemeManager. Total scenes: " + registeredScenes.size());
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
        System.out.println("☀️ Switching to Light Mode");
        applyThemeToAllScenes();
    }

    /**
     * Set dark theme
     */
    public void setDarkTheme() {
        currentTheme = DARK_THEME;
        prefs.put(THEME_PREF_KEY, DARK_THEME);
        System.out.println("🌙 Switching to Dark Mode");
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

        if (isDarkMode()) {
            // Dark theme stylesheets
            addStylesheetIfExists(scene, "dark-theme.css");
            addStylesheetIfExists(scene, "pantry-dark.css");
            addStylesheetIfExists(scene, "Recipe-dark.css");
            addStylesheetIfExists(scene, "styles-dark.css");
            addStylesheetIfExists(scene, "style-dark.css");
            addStylesheetIfExists(scene, "addItem-dark.css");
            addStylesheetIfExists(scene, "ShoppingList-dark.css");
        } else {
            // Light theme stylesheets
            addStylesheetIfExists(scene, "style.css");
            addStylesheetIfExists(scene, "pantry.css");
            addStylesheetIfExists(scene, "Recipe.css");
            addStylesheetIfExists(scene, "styles.css");
            addStylesheetIfExists(scene, "addItem.css");
        }

        System.out.println("  Applied " + scene.getStylesheets().size() + " stylesheets");
    }

    /**
     * Add stylesheet to scene if it exists, searching multiple locations.
     * Search order:
     *   1. /com/example/demo1/{filename}
     *   2. /CSSFiles/{filename}
     */
    private void addStylesheetIfExists(Scene scene, String filename) {
        String stylesheet = null;

        // 1. Try primary location: /com/example/demo1/
        try {
            URL url = getClass().getResource("/com/example/demo1/" + filename);
            if (url != null) {
                stylesheet = url.toExternalForm();
            }
        } catch (Exception e) {
            // ignore
        }

        // 2. Try CSSFiles folder: /CSSFiles/
        if (stylesheet == null) {
            try {
                URL url = getClass().getResource("/CSSFiles/" + filename);
                if (url != null) {
                    stylesheet = url.toExternalForm();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // 3. Try MainApplication's classloader
        if (stylesheet == null) {
            try {
                URL url = MainApplication.class.getResource(filename);
                if (url != null) {
                    stylesheet = url.toExternalForm();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // Add if found
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
            System.out.println("  + Added: " + filename);
        } else {
            // Not an error - some CSS files are optional
            System.out.println("  - Skipped (not found): " + filename);
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