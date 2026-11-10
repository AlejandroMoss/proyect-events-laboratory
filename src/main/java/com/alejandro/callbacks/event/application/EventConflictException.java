package com.alejandro.callbacks.event.application;

public class EventConflictException extends RuntimeException {
  public EventConflictException() {
    super("El identificador ya fue utilizado con un contenido diferente para este origen.");
  }
}
