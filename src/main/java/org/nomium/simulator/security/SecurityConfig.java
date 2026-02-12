package org.nomium.simulator.security;

import org.nomium.simulator.config.SimProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.DigestAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
public class SecurityConfig {

    @Bean
    public DigestAuthenticationEntryPoint digestEntryPoint() {
        var ep = new DigestAuthenticationEntryPoint();
        ep.setRealmName("antminer");
        ep.setKey("simulator-digest-key");
        ep.setNonceValiditySeconds(300);
        return ep;
    }

    @Bean
    public DigestAuthenticationFilter digestAuthenticationFilter(
            UserDetailsService uds,
            DigestAuthenticationEntryPoint entryPoint
    ) {
        var f = new DigestAuthenticationFilter();
        f.setUserDetailsService(uds);
        f.setAuthenticationEntryPoint(entryPoint);
        f.setCreateAuthenticatedToken(true);

        return f;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DigestAuthenticationFilter digestFilter,
            DigestAuthenticationEntryPoint digestEntryPoint
    ) {

        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/test", "/test.html").permitAll()
                .requestMatchers("/cgi-bin/**").authenticated()
                .anyRequest().authenticated()
        );

        http.exceptionHandling(eh -> eh.authenticationEntryPoint(digestEntryPoint));
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.addFilterBefore(digestFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(SimProperties props) {
        UserDetails user = User.withUsername(props.getAuth().getUsername())
                .password("{noop}" + props.getAuth().getPassword())
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}