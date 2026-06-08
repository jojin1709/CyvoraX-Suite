package com.venomproxy.ai;

public record AiConnectionResult(AiProvider provider, boolean success, String message, int statusCode, int modelCount) {
}
