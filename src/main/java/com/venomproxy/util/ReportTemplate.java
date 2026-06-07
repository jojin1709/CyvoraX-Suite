package com.venomproxy.util;

public enum ReportTemplate {
    BUG_BOUNTY("Bug Bounty Report"),
    PENTEST("Pentest Report"),
    EXECUTIVE_SUMMARY("Executive Summary"),
    TECHNICAL_ASSESSMENT("Technical Assessment");

    private final String displayName;

    ReportTemplate(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
