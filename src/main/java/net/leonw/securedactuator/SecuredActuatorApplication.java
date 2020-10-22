package net.leonw.securedactuator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class SecuredActuatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecuredActuatorApplication.class, args);
    }
}

@Service
class MyUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        if ("leon".equals(s)) {
            return User.withUsername("leon").password("{bcrypt}$2a$10$WUhE/XPXxqi0gyTdJUtqGeKIPjcqgTfhqCEiMi8VTe4K7iPqwIYkO").authorities("ROLE_ACTUATOR_ADMIN").build();
        }
        return null;
    }
}

@Configuration(proxyBeanMethods = false)
class ActuatorSecurity extends WebSecurityConfigurerAdapter {
    protected void configure(HttpSecurity http) throws Exception {
        // Note: EndpointRequest is from the actuate package, not the generic Endpoint the name suggests
        http.requestMatcher(EndpointRequest.toAnyEndpoint()).authorizeRequests((requests) ->
                requests.anyRequest().hasRole("ACTUATOR_ADMIN"));
        http.httpBasic();
    }
}


