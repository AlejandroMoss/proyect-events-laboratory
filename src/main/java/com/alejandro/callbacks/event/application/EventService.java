package com.alejandro.callbacks.event.application;

import com.alejandro.callbacks.event.domain.CallbackEvent;
import com.alejandro.callbacks.event.domain.EventRepository;
import com.alejandro.callbacks.event.domain.NewCallback;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  private static final Logger log = LoggerFactory.getLogger(EventService.class);
  private final EventRepository repository;

  public EventService(EventRepository repository) {
    this.repository = repository;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
  public Receipt receive(NewCallback command) {
    var inserted = repository.insert(command);
    if (inserted.isPresent()) {
      var event = inserted.get();
      log.info("Callback stored id={} source={}", event.id(), event.source());
      return new Receipt(event, false);
    }
    // A conflicting INSERT waits for the winning transaction. READ_COMMITTED
    // makes its committed row visible to this subsequent SELECT.
    var existing =
        repository
            .findByExternalId(command.source(), command.eventId())
            .orElseThrow(() -> new IllegalStateException("Conflicting event disappeared"));
    if (!repository.hasSameContent(existing.id(), command)) {
      throw new EventConflictException();
    }
    return new Receipt(existing, true);
  }

  @Transactional(readOnly = true, timeout = 10)
  public CallbackEvent get(UUID id) {
    return repository.findById(id).orElseThrow(EventNotFoundException::new);
  }

  @Transactional(readOnly = true, timeout = 10)
  public EventPage list(String source, String type, int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Paginación fuera de los límites permitidos.");
    }
    var rows = repository.findPage(source, type, size + 1, (long) page * size);
    return new EventPage(
        List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
  }

  public record Receipt(CallbackEvent event, boolean duplicate) {}

  public record EventPage(List<CallbackEvent> items, int page, int size, boolean hasNext) {}
}
