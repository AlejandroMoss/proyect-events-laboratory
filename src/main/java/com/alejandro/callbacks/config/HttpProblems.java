package com.alejandro.callbacks.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

final class HttpProblems {
  private final JsonMapper mapper;

  HttpProblems(JsonMapper mapper) {
    this.mapper = mapper;
  }

  void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
  }
}
