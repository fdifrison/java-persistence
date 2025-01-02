package com.fdifrison.entityrelashionship.many2many.unidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.Accessors;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@SpringBootApplication
@ConfigurationPropertiesScan
public class m2mUnidirectional {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2mUnidirectional.class)
                .profiles(Profiles.Active.many2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {

        };
    }

}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

}

@Repository
interface TagRepository extends JpaRepository<Tag, Long> {

}

@Service
class TestService {

}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "post")
class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private @With String title;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "post_tag",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "tag")
class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private @With String name;
}