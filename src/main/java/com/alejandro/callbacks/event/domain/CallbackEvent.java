package com.alejandro.callbacks.event.domain;

import java.time.Instant;
import java.util.UUID;

public record CallbackEvent(
    UUID id,
    String source,
    String eventId,
    String type,
    Instant occurredAt,
    Instant receivedAt,
    String payload) {}
