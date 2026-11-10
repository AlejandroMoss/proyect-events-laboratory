package com.alejandro.callbacks.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceCredentials.class)
public class SecurityConfiguration {
  @Bean
  UserDetailsService users(ServiceCredentials credentials) {
    var encoder = new BCryptPasswordEncoder();
    var user =
        User.withUsername(credentials.username())
            .password("{bcrypt}" + encoder.encode(credentials.password()))
            .roles("SERVICE")
            .build();
    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, JsonMapper mapper) throws Exception {
    var problems = new HttpProblems(mapper);
    return http
        // Machine-to-machine API: no cookies or browser sessions.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/**", "/actuator/metrics", "/actuator/metrics/**")
                    .hasRole("SERVICE")
                    .anyRequest()
                    .denyAll())
        .httpBasic(
            basic ->
                basic.authenticationEntryPoint(
                    (request, response, exception) -> {
                      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"callbacks\"");
                      problems.write(
                          response, HttpStatus.UNAUTHORIZED, "Se requiere autenticación válida.");
                    }))
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, exception) ->
                        problems.write(response, HttpStatus.FORBIDDEN, "Acceso no permitido.")))
        .addFilterAfter(new RequestBodyLimitFilter(problems), BasicAuthenticationFilter.class)
        .build();
  }
}
