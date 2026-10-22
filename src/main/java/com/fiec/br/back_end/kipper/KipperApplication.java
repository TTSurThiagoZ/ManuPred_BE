package com.fiec.br.back_end.kipper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KipperApplication {
    public static void main(String[] args) {
        SpringApplication.run(KipperApplication.class, args);
    }
}