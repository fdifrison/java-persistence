package com.fdifrison.singletable;

import com.fdifrison.configurations.Profiles;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SingleTable {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SingleTable.class)
                .profiles(Profiles.Active.single_table.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService service) {
        return args -> {

        };
    }
}

class TestService {

}


@Getter
@Setter
@Accessors(fluent = true, chain = true)
@Entity
@Table
class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @OneToMany(mappedBy = Topic_.BOARD)
    private List<Topic> topics = new ArrayList<>();

}

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@Entity
@Table
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) // default inheritance type
class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String owner;
    @CreationTimestamp
    private Instant createdOn;
    @ManyToOne(fetch = FetchType.LAZY)
    private Board board;

}

@Getter
@Setter
@Entity
class Post extends Topic {
    private String content;
}

@Getter
@Setter
@Entity
class Announcement extends Topic {
    private Instant validUntil;
}

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@Entity
@Table
class TopicStatistics {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    private Topic topic;

    private long views;
}



