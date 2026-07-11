package dev.algoj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OjApplication {

    public static void main(String[] args) {
        SpringApplication.run(OjApplication.class, args);
    }

}
