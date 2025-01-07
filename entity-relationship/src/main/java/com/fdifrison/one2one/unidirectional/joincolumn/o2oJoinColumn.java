package com.fdifrison.one2one.unidirectional.joincolumn;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2oJoinColumn {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2oJoinColumn.class)
                .profiles(Profiles.Active.one2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService testService) {
        // TODO there is no difference in terms of performance between the join column and the maps id approach,
        //  however the latter has the potential to give also (indirectly) a bidirectional access from the parent to the
        //  child entity, and to save the space of one useless index for the child primary key
        return args -> {
            Printer.focus("Unidirectional mapping with join column");
            var post = testService.savePost();
            testService.saveDetail(post.id());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Repository
interface DetailRepository extends JpaRepository<Detail, Long> {}

@Service
class TestService {

    private final PostRepository postRepository;
    private final DetailRepository detailRepository;

    TestService(PostRepository postRepository, DetailRepository detailRepository) {
        this.postRepository = postRepository;
        this.detailRepository = detailRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post().withTitle("Title"));
    }

    @Transactional
    public void saveDetail(long postId) {
        var post = postRepository.findById(postId).orElseThrow();
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
    private Long id;

    @Column(nullable = false)
    private @With String title;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity(name = "Detail")
@Table(name = "detail")
class Detail {

    // TODO we have two indexes, one for the primary key and one for the foreign key

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdOn;

    private String createdBy;

    @OneToOne
    @JoinColumn(name = "post_id")
    // TODO @JoinColumn not required since we have a column post_id in the detail table
    private @With Post post;
}
