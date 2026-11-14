package com.kun.aiinterview;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.SpringApplication;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AiInterviewSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiInterviewSystemApplication.class, args);
    }

}
