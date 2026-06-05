package com.venomproxy.proxy;

import com.venomproxy.db.Database;
import com.venomproxy.model.MatchReplaceRule;
import com.venomproxy.model.RequestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchReplaceEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesConditionalRegexBodyReplacement() throws Exception {
        try (Database database = new Database(tempDir.resolve("test.db"))) {
            MatchReplaceRule rule = new MatchReplaceRule(
                    true,
                    "Request",
                    "Body",
                    "token=[a-z0-9]+",
                    "token=REDACTED",
                    true,
                    "URL",
                    "/submit",
                    ""
            );
            database.saveMatchReplaceRule(rule);
            MatchReplaceEngine engine = new MatchReplaceEngine(database);

            RequestData request = RequestData.fromRaw("""
                    POST http://example.test/submit HTTP/1.1
                    Host: example.test
                    Content-Type: application/x-www-form-urlencoded

                    user=jojin&token=abc123
                    """);
            RequestData transformed = engine.applyToRequest(request);

            assertEquals("user=jojin&token=REDACTED\n", new String(transformed.getBody(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void replacesSpecificHeaderCaseInsensitively() throws Exception {
        try (Database database = new Database(tempDir.resolve("test.db"))) {
            database.saveMatchReplaceRule(new MatchReplaceRule(true, "Request", "Header:Authorization",
                    "Bearer old", "Bearer new", false, "", "", ""));
            MatchReplaceEngine engine = new MatchReplaceEngine(database);

            RequestData request = RequestData.fromRaw("""
                    GET http://example.test/ HTTP/1.1
                    Host: example.test
                    authorization: Bearer old

                    """);
            RequestData transformed = engine.applyToRequest(request);

            assertEquals("Bearer new", transformed.getHeaders().get("authorization"));
        }
    }
}
