package org.nomium.sumulator.security;

import org.nomium.sumulator.config.SimProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/health").permitAll()
                .requestMatchers("/cgi-bin/**").authenticated()
                .anyRequest().permitAll()
        );

        http.httpBasic(Customizer.withDefaults());
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
