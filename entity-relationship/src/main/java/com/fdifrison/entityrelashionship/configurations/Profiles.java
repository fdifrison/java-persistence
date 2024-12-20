package com.fdifrison.entityrelashionship.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.profiles")
public record Profiles(Active active) {
    public enum Active {
        many_to_one,
        one_to_one,
        many_to_many
    }
}
