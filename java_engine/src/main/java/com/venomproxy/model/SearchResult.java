package com.venomproxy.model;

public class SearchResult {
    private final String type;
    private final long recordId;
    private final String target;
    private final String matchField;
    private final String match;
    private final String preview;

    public SearchResult(String type, long recordId, String target, String matchField, String match, String preview) {
        this.type = type;
        this.recordId = recordId;
        this.target = target;
        this.matchField = matchField;
        this.match = match;
        this.preview = preview;
    }

    public String getType() {
        return type;
    }

    public long getRecordId() {
        return recordId;
    }

    public String getTarget() {
        return target;
    }

    public String getMatchField() {
        return matchField;
    }

    public String getMatch() {
        return match;
    }

    public String getPreview() {
        return preview;
    }
}
