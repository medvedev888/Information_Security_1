package ru.itmo.secureapi;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/secure_notes}",
        "spring.datasource.username=${TEST_DB_USERNAME:notes_app}",
        "spring.datasource.password=${TEST_DB_PASSWORD}",
        "security.jwt-secret=dGVzdC1vbmx5LWtleS0zMi1ieXRlcy1taW5pbXVtLWxlbmd0aCE=",
        "demo.username=test_student", "demo.password=test-password-12345"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserStore users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;

    @BeforeEach
    void prepare() {
        jdbc.update("DELETE FROM notes WHERE owner_id IN (SELECT id FROM app_users WHERE username IN (?, ?))",
                "test_student", "test_other");
        users.createIfAbsent("test_other", passwords.encode("other-password-12345"));
    }
    String login(String username, String password) throws Exception {
        var result = mvc.perform(post("/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new AuthController.LoginRequest(username, password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
    String token(Instant issued, Instant expires, String issuer, String audience) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("1").issuedAt(issued).expiresAt(expires)
                        .issuer(issuer).audience(List.of(audience)).build())).getTokenValue();
    }
    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/data")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/data").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/data").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void loginRejectsWrongPasswordUnknownUserAndSqlInjection() throws Exception {
        for (String username : List.of("test_student", "unknown", "' OR 1=1 --")) {
            mvc.perform(post("/auth/login").contentType("application/json")
                    .content(json.writeValueAsString(new AuthController.LoginRequest(username, "wrong"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid credentials"));
        }
    }
    @Test
    void passwordsAreBcryptHashesAndSalted() {
        String hash = users.find("test_student").orElseThrow().passwordHash();
        assertThat(hash).startsWith("$2a$12$");
        assertThat(passwords.matches("test-password-12345", hash)).isTrue();
        assertThat(passwords.encode("test-password-12345")).isNotEqualTo(hash);
    }
    @Test
    void createsEscapesAndIsolatesNotes() throws Exception {
        String access = login("test_student", "test-password-12345");
        String payload = json.writeValueAsString(new NotesController.CreateNote(
                "<script>alert('x')</script>", "'); DROP TABLE notes; --"));
        mvc.perform(post("/api/data").header("Authorization", "Bearer " + access)
                .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
        String other = login("test_other", "other-password-12345");
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test
    void rejectsExpiredWrongIssuerWrongAudienceAndTamperedTokens() throws Exception {
        Instant now = Instant.now();
        String valid = login("test_student", "test-password-12345");
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        for (String bad : List.of(tampered,
                token(now.minusSeconds(600), now.minusSeconds(300), "secure-notes-api", "secure-notes-client"),
                token(now, now.plusSeconds(300), "wrong", "secure-notes-client"),
                token(now, now.plusSeconds(300), "secure-notes-api", "wrong"))) {
            mvc.perform(get("/api/data").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized());
        }
    }
    @Test
    void rejectsInvalidInputAndClientSuppliedOwner() throws Exception {
        String access = login("test_student", "test-password-12345");
        for (String payload : List.of("{}", "{", "{\"title\":\"\",\"content\":\"x\"}",
                "{\"title\":\"a\",\"content\":\"b\",\"ownerId\":1}",
                json.writeValueAsString(new NotesController.CreateNote("x".repeat(121), "x")))) {
            mvc.perform(post("/api/data").header("Authorization", "Bearer " + access)
                    .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
    }
}
