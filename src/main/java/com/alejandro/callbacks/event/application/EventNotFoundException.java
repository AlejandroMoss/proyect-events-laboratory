package com.alejandro.callbacks.event.application;

public class EventNotFoundException extends RuntimeException {
  public EventNotFoundException() {
    super("El evento solicitado no existe.");
  }
}
