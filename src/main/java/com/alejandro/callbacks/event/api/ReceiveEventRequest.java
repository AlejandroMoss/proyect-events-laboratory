package com.alejandro.callbacks.event.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record ReceiveEventRequest(
    @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]*") String eventId,
    @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String type,
    @NotNull Instant occurredAt,
    @NotNull JsonNode payload) {

  @AssertTrue(message = "payload debe ser un objeto JSON")
  public boolean isPayloadObject() {
    return payload != null && payload.isObject();
  }

  @AssertTrue(message = "occurredAt debe estar entre los años 0001 y 9999")
  public boolean isTimestampSupported() {
    return occurredAt == null
        || (!occurredAt.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
            && occurredAt.isBefore(Instant.parse("+10000-01-01T00:00:00Z")));
  }
}
