package com.venomproxy.ui;

import com.venomproxy.db.Database;
import javafx.scene.Scene;

import java.util.Map;

public class ThemeManager {
    private static final String SETTING_KEY = "theme";
    private static final Map<String, String> THEME_FILES = Map.of(
            "CyvoraX Navy/Teal", "/styles/navy-teal-theme.css",
            "Dark", "/styles/dark-theme.css",
            "Light", "/styles/light-theme.css"
    );

    private final Database database;
    private String currentTheme;

    public ThemeManager(Database database) {
        this.database = database;
        this.currentTheme = database.getSetting(SETTING_KEY, "CyvoraX Navy/Teal");
    }

    public String currentTheme() {
        return currentTheme;
    }

    public void apply(Scene scene, String theme) {
        String requested = THEME_FILES.containsKey(theme) ? theme : "CyvoraX Navy/Teal";
        scene.getStylesheets().clear();
        String resource = THEME_FILES.get(requested);
        scene.getStylesheets().add(getClass().getResource(resource).toExternalForm());
        currentTheme = requested;
        database.setSetting(SETTING_KEY, requested);
    }
}
