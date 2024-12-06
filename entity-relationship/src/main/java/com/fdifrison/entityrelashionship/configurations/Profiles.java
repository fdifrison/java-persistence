package com.fdifrison.entityrelashionship.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.profiles")
public record Profiles(Active active) {
    enum Active {
        many_to_one,
        one_to_many
    }
}
