package com.fdifrison.entityrelashionship.one2one.unidirectional.joincolumn;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import com.fdifrison.entityrelashionship.one2one.unidirectional.mapsid.o2oMapsId;
import jakarta.persistence.*;
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

import java.time.Instant;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2oJoinColumn {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2oMapsId.class)
                .profiles(Profiles.Active.one2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {
            var post = postService.savePost();
            postService.saveDetail(post);
        };
    }

}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {
}

@Repository
interface DetailRepository extends JpaRepository<Detail, Long> {
}



@Service
class PostService {

    private final PostRepository postRepository;
    private final DetailRepository detailRepository;

    PostService(PostRepository postRepository, DetailRepository detailRepository) {
        this.postRepository = postRepository;
        this.detailRepository = detailRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post().withTitle("Title"));
    }

    public void saveDetail(Post post) {
        detailRepository.save(new Detail().withPost(post));
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
@Table(name = "detail")
class Detail {

    // TODO we have two indexes, one for the primary key and one for the foreign key

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdOn;

    private String createdBy;

    @OneToOne
    @JoinColumn(name = "post_id")
    // TODO @JoinColumn not required since we have a column post_id in the detail table
    private @With Post post;
}
