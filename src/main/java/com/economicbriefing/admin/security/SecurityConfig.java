package com.economicbriefing.admin.security;

import com.economicbriefing.config.AdminProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper;
    private final boolean requireHttps;

    public SecurityConfig(AdminProperties adminProperties, ObjectMapper objectMapper,
                          @Value("${auth.require-https:true}") boolean requireHttps) {
        this.adminProperties = adminProperties;
        this.objectMapper = objectMapper;
        this.requireHttps = requireHttps;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(200))
            )
            .addFilterBefore(
                new AdminTokenFilter(adminProperties.token(), objectMapper),
                UsernamePasswordAuthenticationFilter.class
            );

        if (requireHttps) {
            http.requiresChannel(channel -> channel
                    .requestMatchers("/api/auth/**").requiresSecure());
        }

        return http.build();
    }
}
