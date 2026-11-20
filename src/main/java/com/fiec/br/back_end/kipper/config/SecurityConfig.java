package com.fiec.br.back_end.kipper.config;

import com.fiec.br.back_end.kipper.features.user.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final OAuth2SuccessHandler oauth2SuccessHandler;

    private final UserRepository userRepository;

    // TEMPORÁRIO: libera o /mcp para agentes de IA sem token (ver McpAcessoLivreFilter)
    @Value("${app.mcp.acesso-livre.habilitado:false}")
    private boolean mcpAcessoLivre;

    @Value("${app.mcp.acesso-livre.email:}")
    private String mcpAcessoLivreEmail;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AuthenticationProvider authenticationProvider,
                          OAuth2SuccessHandler oauth2SuccessHandler, UserRepository userRepository) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
        this.oauth2SuccessHandler = oauth2SuccessHandler;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/mcp/**").permitAll()
                        .requestMatchers("/api/v1/users/auth/firebase").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-theme.css", "/swagger-filtro.js").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        // Listagens públicas (o app e o site mostram antes do login)
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/tipos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/setores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/locais").permitAll()
                        // CORREÇÃO: status da IA pode ser checado sem login
                        .requestMatchers(HttpMethod.GET, "/api/v1/ia/status").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authenticationProvider(authenticationProvider)
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oauth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (mcpAcessoLivre) {
            http.addFilterAfter(new McpAcessoLivreFilter(userRepository, mcpAcessoLivreEmail), JwtAuthFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}