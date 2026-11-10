package com.alejandro.callbacks.event.api;

import com.alejandro.callbacks.event.application.EventService;
import com.alejandro.callbacks.event.domain.NewCallback;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
public class EventController {
  private final EventService service;
  private final JsonMapper mapper;
  private final ReceiveEventRequest request;

  public EventController(EventService service, JsonMapper mapper, ReceiveEventRequest request) {
    this.service = service;
    this.mapper = mapper;
    this.request = request;
  }

  @PostMapping(path = "/callbacks/{source}", consumes = "application/json")
  public ResponseEntity<ReceiptResponse> receive(
      @PathVariable @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,63}") String source,
      @Valid @RequestBody ReceiveEventRequest request) {
    var receipt =
        service.receive(
            new NewCallback(
                source,
                request.eventId(),
                request.type(),
                request.occurredAt(),
                mapper.writeValueAsString(request.payload())));
    var location = URI.create("/api/v1/events/" + receipt.event().id());
    return ResponseEntity.status(receipt.duplicate() ? 200 : 201)
        .location(location)
        .body(
            new ReceiptResponse(EventResponse.from(receipt.event(), mapper), receipt.duplicate()));
  }

  @GetMapping("/events/{id}")
  public EventResponse get(@PathVariable UUID id) {
    return EventResponse.from(service.get(id), mapper);
  }

  @GetMapping("/events")
  public PageResponse list(
      @RequestParam(required = false) @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,63}") String source,
      @RequestParam(required = false)
          @Size(max = 128)
          @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*")
          String type,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var result = service.list(source, type, page, size);
    return new PageResponse(
        result.items().stream().map(e -> EventResponse.from(e, mapper)).toList(),
        result.page(),
        result.size(),
        result.hasNext());
  }

  public record ReceiptResponse(EventResponse event, boolean duplicate) {}

  public record PageResponse(List<EventResponse> items, int page, int size, boolean hasNext) {}
}
