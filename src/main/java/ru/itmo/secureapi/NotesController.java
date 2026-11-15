package ru.itmo.secureapi;

import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/data")
public class NotesController {
    private final JdbcTemplate jdbc;
    public NotesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record CreateNote(@NotBlank @Size(max = 120) String title,
                             @NotBlank @Size(max = 2000) String content) {}
    public record Note(long id, String title, String content, OffsetDateTime createdAt) {}
    private static String escape(String value) { return HtmlUtils.htmlEscape(value, "UTF-8"); }

    @GetMapping
    public List<Note> list(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.query("SELECT id, title, content, created_at FROM notes WHERE owner_id = ? ORDER BY id DESC LIMIT 100",
                (rs, row) -> new Note(rs.getLong("id"), escape(rs.getString("title")),
                        escape(rs.getString("content")), rs.getObject("created_at", OffsetDateTime.class)),
                Long.parseLong(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Note create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateNote request) {
        return jdbc.queryForObject("INSERT INTO notes(owner_id, title, content) VALUES (?, ?, ?) RETURNING id, title, content, created_at",
                (rs, row) -> new Note(rs.getLong("id"), escape(rs.getString("title")),
                        escape(rs.getString("content")), rs.getObject("created_at", OffsetDateTime.class)),
                Long.parseLong(jwt.getSubject()), request.title(), request.content());
    }
}
