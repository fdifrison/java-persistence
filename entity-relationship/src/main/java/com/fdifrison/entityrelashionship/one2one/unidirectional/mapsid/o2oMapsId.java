package com.fdifrison.entityrelashionship.one2one.unidirectional.mapsid;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2oMapsId {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2oMapsId.class)
                .profiles(Profiles.Active.one2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {};
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Service
class PostService {

    private final PostRepository postRepository;

    PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }
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
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "detail_with_maps_id")
class DetailWithMapsId {

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdOn;

    private String createdBy;

    @Id
    @OneToOne
    @MapsId
    private @With Post post;
}
