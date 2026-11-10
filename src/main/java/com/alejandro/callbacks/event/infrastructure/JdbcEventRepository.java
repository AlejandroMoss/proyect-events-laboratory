package com.alejandro.callbacks.event.infrastructure;

import com.alejandro.callbacks.event.domain.CallbackEvent;
import com.alejandro.callbacks.event.domain.EventRepository;
import com.alejandro.callbacks.event.domain.NewCallback;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventRepository implements EventRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public JdbcEventRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CallbackEvent> insert(NewCallback event) {
    return jdbc
        .query(
            """
        INSERT INTO callback_events (id, source, external_id, event_type, occurred_at, payload)
        VALUES (:id, :source, :eventId, :type, :occurredAt, CAST(:payload AS jsonb))
        ON CONFLICT (source, external_id) DO NOTHING
        RETURNING *
        """,
            parameters(event).addValue("id", UUID.randomUUID()),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CallbackEvent> findById(UUID id) {
    return jdbc
        .query(
            "SELECT * FROM callback_events WHERE id = :id",
            new MapSqlParameterSource("id", id),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CallbackEvent> findByExternalId(String source, String eventId) {
    return jdbc
        .query(
            """
        SELECT * FROM callback_events WHERE source = :source AND external_id = :eventId
        """,
            new MapSqlParameterSource("source", source).addValue("eventId", eventId),
            this::map)
        .stream()
        .findFirst();
  }

  @Override
  public boolean hasSameContent(UUID id, NewCallback event) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        SELECT EXISTS (
          SELECT 1 FROM callback_events
          WHERE id = :id AND event_type = :type AND occurred_at = :occurredAt
            AND payload = CAST(:payload AS jsonb)
        )
        """,
            parameters(event).addValue("id", id),
            Boolean.class));
  }

  @Override
  public List<CallbackEvent> findPage(String source, String type, int limit, long offset) {
    var sql = new StringBuilder("SELECT * FROM callback_events WHERE 1 = 1");
    var params = new MapSqlParameterSource("limit", limit).addValue("offset", offset);
    if (source != null) {
      sql.append(" AND source = :source");
      params.addValue("source", source);
    }
    if (type != null) {
      sql.append(" AND event_type = :type");
      params.addValue("type", type);
    }
    sql.append(" ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset");
    return jdbc.query(sql.toString(), params, this::map);
  }

  private MapSqlParameterSource parameters(NewCallback event) {
    return new MapSqlParameterSource("source", event.source())
        .addValue("eventId", event.eventId())
        .addValue("type", event.type())
        .addValue("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
        .addValue("payload", event.payload());
  }

  private CallbackEvent map(ResultSet row, int index) throws SQLException {
    return new CallbackEvent(
        row.getObject("id", UUID.class),
        row.getString("source"),
        row.getString("external_id"),
        row.getString("event_type"),
        row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
        row.getObject("received_at", OffsetDateTime.class).toInstant(),
        row.getString("payload"));
  }
}
