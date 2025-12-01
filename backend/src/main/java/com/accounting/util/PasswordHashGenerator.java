package com.accounting.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java PasswordHashGenerator <password>");
            System.exit(1);
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String password = args[0];
        String hash = encoder.encode(password);

        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash (rounds=12):");
        System.out.println(hash);
    }
}
