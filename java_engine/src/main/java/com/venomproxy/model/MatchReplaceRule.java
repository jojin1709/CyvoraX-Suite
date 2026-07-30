package com.venomproxy.model;

public class MatchReplaceRule {
    private long id;
    private boolean enabled;
    private String phase;
    private String target;
    private String pattern;
    private String replacement;
    private boolean regex;
    private String conditionField;
    private String conditionPattern;
    private String notes;

    public MatchReplaceRule(boolean enabled, String phase, String target, String pattern, String replacement,
                            boolean regex, String conditionField, String conditionPattern, String notes) {
        this.enabled = enabled;
        this.phase = emptyToDefault(phase, "Request");
        this.target = emptyToDefault(target, "Body");
        this.pattern = pattern == null ? "" : pattern;
        this.replacement = replacement == null ? "" : replacement;
        this.regex = regex;
        this.conditionField = conditionField == null ? "" : conditionField;
        this.conditionPattern = conditionPattern == null ? "" : conditionPattern;
        this.notes = notes == null ? "" : notes;
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = emptyToDefault(phase, "Request");
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = emptyToDefault(target, "Body");
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern == null ? "" : pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement == null ? "" : replacement;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public String getConditionField() {
        return conditionField;
    }

    public void setConditionField(String conditionField) {
        this.conditionField = conditionField == null ? "" : conditionField;
    }

    public String getConditionPattern() {
        return conditionPattern;
    }

    public void setConditionPattern(String conditionPattern) {
        this.conditionPattern = conditionPattern == null ? "" : conditionPattern;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }
}
