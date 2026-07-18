package com.forexbot.config;

import com.forexbot.service.OAuth2UserServiceImpl;
import com.forexbot.service.UserDetailsServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final OAuth2UserServiceImpl  oauth2UserService;

    @Value("${spring.security.oauth2.client.registration.google.client-id:disabled}")
    private String googleClientId;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          OAuth2UserServiceImpl oauth2UserService) {
        this.userDetailsService = userDetailsService;
        this.oauth2UserService  = oauth2UserService;
    }

    private boolean isOAuth2Enabled() {
        return googleClientId != null
            && !googleClientId.isBlank()
            && !googleClientId.equals("disabled");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/features", "/pricing", "/about", "/error").permitAll()
                .requestMatchers("/login", "/register", "/forgot-password", "/reset-password").permitAll()
                .requestMatchers("/invite/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/settings/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        if (isOAuth2Enabled()) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .userInfoEndpoint(u -> u.userService(oauth2UserService))
            );
            log.info("Security: Google OAuth2 enabled");
        } else {
            log.info("Security: Google OAuth2 disabled (GOOGLE_CLIENT_ID not set) — username/password only");
        }

        return http.build();
    }
}
