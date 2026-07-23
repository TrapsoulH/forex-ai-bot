package com.forexbot.config;

import com.forexbot.security.LoginRateLimitFilter;
import com.forexbot.service.OAuth2UserServiceImpl;
import com.forexbot.service.UserDetailsServiceImpl;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final OAuth2UserServiceImpl  oauth2UserService;
    private final LoginRateLimitFilter   loginRateLimitFilter;

    @Value("${spring.security.oauth2.client.registration.google.client-id:disabled}")
    private String googleClientId;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          OAuth2UserServiceImpl oauth2UserService,
                          LoginRateLimitFilter loginRateLimitFilter) {
        this.userDetailsService   = userDetailsService;
        this.oauth2UserService    = oauth2UserService;
        this.loginRateLimitFilter = loginRateLimitFilter;
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
                // Spring Security 6 re-checks auth on ASYNC dispatcher (used by SseEmitter).
                // Permit async re-dispatches globally — the original request was already authorised.
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/", "/features", "/pricing", "/about", "/error").permitAll()
                .requestMatchers("/login", "/register", "/forgot-password", "/reset-password").permitAll()
                .requestMatchers("/invite/**").permitAll()
                .requestMatchers("/verify-email/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/settings/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((req, res, auth) -> {
                    boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    res.sendRedirect(isAdmin ? "/admin/users" : "/dashboard");
                })
                .failureHandler((req, res, ex) -> {
                    if (ex instanceof LockedException) {
                        res.sendRedirect("/login?locked");
                    } else if (ex instanceof DisabledException
                            && "EMAIL_NOT_VERIFIED".equals(ex.getMessage())) {
                        res.sendRedirect("/login?unverified");
                    } else {
                        res.sendRedirect("/login?error");
                    }
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?loggedout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/invite/accept")
            );

        if (isOAuth2Enabled()) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler((req, res, auth) -> {
                    boolean isAdmin = auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    res.sendRedirect(isAdmin ? "/admin/users" : "/dashboard");
                })
                .userInfoEndpoint(u -> u.userService(oauth2UserService))
            );
            log.info("Security: Google OAuth2 enabled");
        } else {
            log.info("Security: Google OAuth2 disabled (GOOGLE_CLIENT_ID not set) — username/password only");
        }

        http.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
