package com.venomproxy.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TextCodecs {
    private TextCodecs() {
    }

    public static String apply(String operation, String input) {
        try {
            return switch (operation) {
                case "Base64 Encode" -> Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
                case "Base64 Decode" -> new String(Base64.getDecoder().decode(input.trim()), StandardCharsets.UTF_8);
                case "URL Encode" -> URLEncoder.encode(input, StandardCharsets.UTF_8);
                case "URL Decode" -> URLDecoder.decode(input, StandardCharsets.UTF_8);
                case "HTML Encode" -> htmlEncode(input);
                case "HTML Decode" -> htmlDecode(input);
                case "Hex Encode" -> HexFormat.of().formatHex(input.getBytes(StandardCharsets.UTF_8));
                case "Hex Decode" -> new String(HexFormat.of().parseHex(input.replaceAll("\\s+", "")), StandardCharsets.UTF_8);
                case "Binary Encode" -> toBinary(input);
                case "Binary Decode" -> fromBinary(input);
                case "Gzip Encode" -> gzip(input);
                case "Gzip Decode" -> gunzip(input);
                case "MD5" -> digest("MD5", input);
                case "SHA1" -> digest("SHA-1", input);
                case "SHA256" -> digest("SHA-256", input);
                case "SHA512" -> digest("SHA-512", input);
                case "JWT Decode" -> decodeJwt(input);
                case "Smart Decode" -> smartDecode(input);
                default -> input;
            };
        } catch (Exception ex) {
            return "Error: " + ex.getMessage();
        }
    }

    private static String htmlEncode(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }

    private static String htmlDecode(String input) {
        return input.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#x27;", "'").replace("&amp;", "&");
    }

    private static String toBinary(String input) {
        StringBuilder builder = new StringBuilder();
        for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
            builder.append(String.format("%8s", Integer.toBinaryString(b & 0xff)).replace(' ', '0')).append(' ');
        }
        return builder.toString().trim();
    }

    private static String fromBinary(String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String part : input.trim().split("\\s+")) {
            out.write(Integer.parseInt(part, 2));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String gzip(String input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(input.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private static String gunzip(String input) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(input.trim());
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String digest(String algorithm, String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decodeJwt(String input) {
        String[] parts = input.trim().split("\\.");
        if (parts.length < 2) {
            return "Not a JWT";
        }
        Base64.Decoder decoder = Base64.getUrlDecoder();
        String header = new String(decoder.decode(pad(parts[0])), StandardCharsets.UTF_8);
        String payload = new String(decoder.decode(pad(parts[1])), StandardCharsets.UTF_8);
        return "Header:\n" + header + "\n\nPayload:\n" + payload + "\n\nSignature present: " + (parts.length > 2);
    }

    private static String pad(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }

    private static String smartDecode(String input) {
        String trimmed = input.trim();
        if (trimmed.split("\\.").length >= 2) {
            return decodeJwt(trimmed);
        }
        if (trimmed.matches("(?i)[0-9a-f\\s]+") && trimmed.replaceAll("\\s+", "").length() % 2 == 0) {
            return apply("Hex Decode", trimmed);
        }
        if (trimmed.matches("[A-Za-z0-9+/=_-]+")) {
            return apply("Base64 Decode", trimmed);
        }
        if (trimmed.contains("%")) {
            return apply("URL Decode", trimmed);
        }
        return input;
    }
}
