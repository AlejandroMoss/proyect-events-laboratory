package com.alejandro.callbacks.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alejandro.callbacks.event.domain.CallbackEvent;
import com.alejandro.callbacks.event.domain.EventRepository;
import com.alejandro.callbacks.event.domain.NewCallback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
  @Mock EventRepository repository;
  EventService service;
  final NewCallback command =
      new NewCallback(
          "payments", "evt-1", "payment.created", Instant.parse("2026-01-01T00:00:00Z"), "{}");
  final CallbackEvent event =
      new CallbackEvent(
          UUID.randomUUID(),
          command.source(),
          command.eventId(),
          command.type(),
          command.occurredAt(),
          Instant.now(),
          command.payload());

  @BeforeEach
  void setUp() {
    service = new EventService(repository);
  }

  @Test
  void storesNewEvent() {
    when(repository.insert(command)).thenReturn(Optional.of(event));
    assertThat(service.receive(command)).isEqualTo(new EventService.Receipt(event, false));
  }

  @Test
  void returnsIdenticalRetry() {
    existing();
    when(repository.hasSameContent(event.id(), command)).thenReturn(true);
    assertThat(service.receive(command)).isEqualTo(new EventService.Receipt(event, true));
  }

  @Test
  void rejectsReusedIdentifier() {
    existing();
    when(repository.hasSameContent(event.id(), command)).thenReturn(false);
    assertThatThrownBy(() -> service.receive(command)).isInstanceOf(EventConflictException.class);
  }

  @Test
  void reportsMissingEvent() {
    when(repository.findById(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(EventNotFoundException.class);
  }

  @Test
  void fetchesOneExtraRowToDetermineNextPage() {
    when(repository.findPage(null, null, 2, 2)).thenReturn(List.of(event, event));
    var page = service.list(null, null, 2, 1);
    assertThat(page.items()).hasSize(1);
    assertThat(page.hasNext()).isTrue();
    verify(repository).findPage(null, null, 2, 2);
  }

  @Test
  void rejectsInvalidPagination() {
    assertThatThrownBy(() -> service.list(null, null, -1, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(null, null, 0, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalizesTimestampToDatabasePrecision() {
    var precise =
        new NewCallback("a", "b", "c", Instant.parse("2026-01-01T00:00:00.123456789Z"), "{}");
    assertThat(precise.occurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
  }

  private void existing() {
    when(repository.insert(command)).thenReturn(Optional.empty());
    when(repository.findByExternalId(command.source(), command.eventId()))
        .thenReturn(Optional.of(event));
  }
}
