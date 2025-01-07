package com.fdifrison.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.profiles")
public record Profiles(Active active) {
    public enum Active {
        single_table,
        joined,
        table_per_class,
        mapped_superclass,
    }
}
