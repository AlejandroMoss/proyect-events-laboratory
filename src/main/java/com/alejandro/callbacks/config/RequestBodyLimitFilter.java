package com.alejandro.callbacks.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

final class RequestBodyLimitFilter extends OncePerRequestFilter {
  static final int MAX_BYTES = 1_048_576;
  private final HttpProblems problems;

  RequestBodyLimitFilter(HttpProblems problems) {
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod())
        || !request.getServletPath().startsWith("/api/v1/callbacks/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      reject(response);
      return;
    }
    // Also enforce the limit when Content-Length is absent (chunked requests).
    var bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      reject(response);
      return;
    }
    var stream = new ByteArrayInputStream(bytes);
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            return new ServletInputStream() {
              @Override
              public int read() {
                return stream.read();
              }

              @Override
              public boolean isFinished() {
                return stream.available() == 0;
              }

              @Override
              public boolean isReady() {
                return true;
              }

              @Override
              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous request body only");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
          }
        },
        response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    problems.write(response, HttpStatus.CONTENT_TOO_LARGE, "El cuerpo no puede superar 1 MiB.");
  }
}
