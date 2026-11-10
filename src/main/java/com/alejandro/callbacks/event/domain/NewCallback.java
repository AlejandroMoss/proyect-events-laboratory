package com.alejandro.callbacks.event.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record NewCallback(
    String source, String eventId, String type, Instant occurredAt, String payload) {
  public NewCallback {
    // PostgreSQL stores microseconds: normalize before comparing retried events.
    occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
  }
}
