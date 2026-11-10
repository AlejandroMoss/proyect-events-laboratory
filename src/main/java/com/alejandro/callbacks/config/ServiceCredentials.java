package com.alejandro.callbacks.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("callbacks.security")
public record ServiceCredentials(
    @NotBlank @Size(max = 64) String username,
    @NotBlank @Size(min = 16, max = 72) @Pattern(regexp = "[\\x21-\\x7E]+") String password) {
  @Override
  public String toString() {
    return "ServiceCredentials[REDACTED]";
  }
}
