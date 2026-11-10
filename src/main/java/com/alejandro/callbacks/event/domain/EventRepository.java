package com.alejandro.callbacks.event.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {
  Optional<CallbackEvent> insert(NewCallback event);

  Optional<CallbackEvent> findById(UUID id);

  Optional<CallbackEvent> findByExternalId(String source, String eventId);

  boolean hasSameContent(UUID id, NewCallback event);

  List<CallbackEvent> findPage(String source, String type, int limit, long offset);
}
