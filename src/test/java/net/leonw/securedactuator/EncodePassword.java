package net.leonw.securedactuator;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class EncodePassword {

    public static void main(String[] args) {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        System.out.println(encoder.encode("secret"));
    }
}
// {bcrypt}$2a$10$WUhE/XPXxqi0gyTdJUtqGeKIPjcqgTfhqCEiMi8VTe4K7iPqwIYkO