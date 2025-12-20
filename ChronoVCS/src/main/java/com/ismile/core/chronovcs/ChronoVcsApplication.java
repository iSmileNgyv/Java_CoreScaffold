package com.ismile.core.chronovcs;

import com.ismile.core.chronovcs.config.security.JwtProperties;
import com.ismile.core.chronovcs.service.storage.impl.LocalStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication
@EnableConfigurationProperties({LocalStorageProperties.class, JwtProperties.class})
public class ChronoVcsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronoVcsApplication.class, args);
    }

}
