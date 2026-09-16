package com.devtunde.posbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosBackendApplication.class, args);
    }
}
