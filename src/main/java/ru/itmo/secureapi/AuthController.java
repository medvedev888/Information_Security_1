package ru.itmo.secureapi;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserStore users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String dummyHash;
    private final String issuer;
    private final String audience;
    private final long ttl;
    public AuthController(UserStore users, PasswordEncoder passwords, JwtEncoder encoder,
            @Value("${security.issuer}") String issuer, @Value("${security.audience}") String audience,
            @Value("${security.token-ttl-seconds}") long ttl) {
        this.users = users; this.passwords = passwords; this.encoder = encoder;
        this.issuer = issuer; this.audience = audience; this.ttl = ttl;
        this.dummyHash = passwords.encode("dummy-password-for-timing-only");
    }
    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 72) String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too long");
        var user = users.find(request.username());
        boolean matches = passwords.matches(request.password(), user.map(UserStore.User::passwordHash).orElse(dummyHash));
        if (!matches || user.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(Long.toString(user.orElseThrow().id())).issuedAt(now).expiresAt(now.plusSeconds(ttl)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl);
    }
}
