package ru.itmo.secureapi;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUserInitializer implements CommandLineRunner {
    private final UserStore users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    public DemoUserInitializer(UserStore users, PasswordEncoder encoder,
            @Value("${demo.username}") String username, @Value("${demo.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }
    @Override
    public void run(String... args) {
        if (!username.matches("[a-zA-Z0-9_]{3,50}") || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Invalid demo credentials: username 3-50, password 12-72 bytes");
        }
        if (users.find(username).isEmpty()) users.createIfAbsent(username, encoder.encode(password));
    }
}
