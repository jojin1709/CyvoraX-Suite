package com.venomproxy.auth;

import com.venomproxy.db.Database;
import com.venomproxy.model.AuthAccount;
import com.venomproxy.model.RequestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesActiveMatchingTokenAndCookieJar() throws Exception {
        try (Database database = new Database(tempDir.resolve("auth.db"))) {
            AuthenticationManager manager = new AuthenticationManager(database);
            manager.save(new AuthAccount("admin", "*.example.test", "token-123", "sid=abc; role=admin", "", true));

            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put("Host", "api.example.test");
            RequestData request = new RequestData("GET", "https://api.example.test/profile", headers, new byte[0]);

            manager.apply(request);

            assertEquals("Bearer token-123", request.getHeaders().get("Authorization"));
            assertEquals("sid=abc; role=admin", request.getHeaders().get("Cookie"));
        }
    }
}
