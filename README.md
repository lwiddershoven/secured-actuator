

# Spring Boot application with Actuator


These are my notes from the development (and discovery) process. I may at some point clean them up, and then again, I might not.

The reason this project exists is because I really like having these standard endpoints available, also on prod, but these endpoints can obviously not be accissible by just anyone. 
So, apart from protecting them from the big bad internet by [removing that location from the proxy](https://serverfault.com/questions/816998/nginx-rule-match-all-paths-except-one) you probably also want to have some control on who can view the contents (which may contain protected data). 

## Initializr

https://start.spring.io/

- lombok
- actuator
- devtools
- web

And will need
- security
- possibly  cloud / kubernetes when moving to secrets

Pushed to github using tool gh

## Enabling health endpoints

From a generated project (no extra config, localhost) the actuator can be found on [/actuator](http://localhost:8080/actuator). This gives a /health and /info endpoint.

To view these in a decent way, do not forget to install a json formatting plugin like JSON Formatter. 

By default most endpoints are exposed on JMX, but not web. To enable the endpoints for web, add the configuration `management.endpoints.web.exposure.include=*` or `management.endpoints.web.exposure.include=health,info,other_endpoints`

Documentation about the spring boot actuator functionalities can be found in the [spring documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html)

Be very aware that not only do these endpoints show a lot of the internals of your application in  very standardized (and thus exploitable) way; some of these endpoints like `threaddump` and `heapdump` can effectively kill your application with repeated use.

```properties
management.endpoints.web.exposure.include=*
management.endpoints.web.exposure.exclude=threaddump,heapdump
```


## Securing health endpoints

### A different location

While certainly not a security feature it may make sense to move the actuator endpoint to a location not reachable by the internet if such a locataion exists. It may that the pattern '/internal' is filtered out by the proxies that filter incoming traffic (if any), and it would then make sense to move the management endpoint to be in that context. 

```properties
management.endpoints.web.base-path=/internal
```

It is even possible to place the management interface on a different port. That may make sense on a Kubernetes environment where all 8080 traffic is exposed through the service, but you can apply port-forwarding to your own machine (if you only need it for your own purposes).
```properties
management.server.port=8081
```

### Adding a hardcoded username/password

... in a way that the password value cannot be discovered through the /actuator/env endpoint, and is not compiled into the code.

First add spring-security to the pom.xml:
```xml
<dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

This immediatetely secures everything with a user `user` and a generated password that can be found in the startup logs, like `Using generated security password: e0cd0073-1a32-4996-bc87-5c3030bf08cc`.

Securing all endpoints with the same security is probably not what you want, so let's limit this security to the actuator endpoints.

#### Hashing the password

Committing passwords in the code or otherwise is a bad idea in the best of times, and in production software it is a particular bad idea. So encode the password to a hash that can not be reversed, and include that hash in the code:

```java
public static void main(String[] args) {
	PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	System.out.println(encoder.encode("secret"));
}
```

Do note that every run generates a different hashed value. I will go with the password `secret`, which hashed has become `{bcrypt}$2a$10$WUhE/XPXxqi0gyTdJUtqGeKIPjcqgTfhqCEiMi8VTe4K7iPqwIYkO`.

#### Adding the security configuration

I chose to add it to my application 
```java

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
            return User.withUsername("leon")
				.password("{bcrypt}$2a$10$WUhE/XPXxqi0gyTdJUtqGeKIPjcqgTfhqCEiMi8VTe4K7iPqwIYkO")
				.authorities("ROLE_ACTUATOR_ADMIN")
				.build();
        }
        return null;
    }
}

@Configuration(proxyBeanMethods = false)
class ActuatorSecurity extends WebSecurityConfigurerAdapter {
    protected void configure(HttpSecurity http) throws Exception {
        // Note: EndpointRequest is from the actuate package, not the generic Endpoint the name suggests
        http.requestMatcher(EndpointRequest.toAnyEndpoint()).authorizeRequests((requests) ->
                requests.anyRequest().hasRole("ACTUATOR_ADMIN")); // do not prefix with ROLE_
        http.httpBasic();
    }
}
```

#### proxyBeanMethods = false

If you don't call bean-generating methods directly in your configuration class this should be safe, and preferred since it is a bit faster and more efficient. 

If you do refer to beans by method make sure you have proxyBeanMethods = true (the default).

```java
@Configuration
public class TestConf {
	
	@Bean
	ServiceA serviceA() { return new ServiceA();	} 
	
	@Bean 
	ServiceB serviceB() { return new ServiceB(serviceA());}
}
```

If you would deactivate the proxy bean methods there would be 2 distinct serviceA instances. With the proxyBean annotation you enable CGLib to return the same serviceA instance to Spring directly, and for the construction of serviceB.

#### Logging

In case of trouble do not forget that it is very easy to configure the logging through `application.properties`. For instance you could add the line `logging.level.org.springframework.security=TRACE`to see what spring security is doing. I would recommend this even if everything works right as it does show you how it works, and the various options that are offered.

## Github Actions


```yaml

name: Maven Build

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2
    - name: Set up JDK 11
      uses: actions/setup-java@v1
      with: 
        java-version: 11
    - name: mvn compile
      # -B : batch mode (non-interactive)
      # -D... Remove all these "Downloading / Downloaded" messages.A
      run: mvn -B compile --file pom.xml -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
    - name: mvn test
      run: mvn -B test --file pom.xml -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
    - name: mvn package
      run: mvn -B package --file pom.xml -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
```
