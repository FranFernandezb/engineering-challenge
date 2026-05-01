package yuno.challenge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security config for the demo.
 * <p>
 * Spring Security is on the classpath (because real Yuno production services need
 * authn/z) but for this local-only demo we open all endpoints so a reviewer can
 * curl/Postman without managing tokens. In a real deployment this would be replaced
 * with JWT / OAuth2 resource-server config.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(f -> f.disable())); // allow H2 console iframe
        return http.build();
    }
}
