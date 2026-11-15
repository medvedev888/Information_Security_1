package ru.itmo.secureapi;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserStore {
    private final JdbcTemplate jdbc;
    public UserStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record User(long id, String username, String passwordHash) {}
    public Optional<User> find(String username) {
        return jdbc.query("SELECT id, username, password_hash FROM app_users WHERE username = ?",
                (rs, row) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash")),
                username).stream().findFirst();
    }
    public void createIfAbsent(String username, String hash) {
        jdbc.update("INSERT INTO app_users(username, password_hash) VALUES (?, ?) ON CONFLICT (username) DO NOTHING",
                username, hash);
    }
}
