package com.xd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CodeFixApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeFixApplication.class, args);
    }

}
