package com.alejandro.callbacks.event.api;

import com.alejandro.callbacks.event.domain.CallbackEvent;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public record EventResponse(
    UUID id,
    String source,
    String eventId,
    String type,
    Instant occurredAt,
    Instant receivedAt,
    JsonNode payload) {
  static EventResponse from(CallbackEvent event, JsonMapper mapper) {
    return new EventResponse(
        event.id(),
        event.source(),
        event.eventId(),
        event.type(),
        event.occurredAt(),
        event.receivedAt(),
        mapper.readTree(event.payload()));
  }
}
