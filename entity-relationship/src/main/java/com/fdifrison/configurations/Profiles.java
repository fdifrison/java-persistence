package com.fdifrison.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.profiles")
public record Profiles(Active active) {
    public enum Active {
        many2one,
        one2many,
        one2one,
        many2many
    }
}
