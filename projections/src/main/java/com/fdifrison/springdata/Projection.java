package com.fdifrison.springdata;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Projection {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Projection.class)
                .profiles(Profiles.Active.projection.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService service, PostRepository repository) {
        return args -> {
            if (repository.count() < 100) {
                for (int i = 0; i < 100; i++) {
                    service.addBaseData();
                }
            }
            service.compareQueryMethods();
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

    // TODO the only advantage of the projection in this case is that we can reduce the number of columns fetched
    //  by hibernate

    @EntityGraph(attributePaths = "comments")
    @Query("select p.id as id, p.title as title, c.comment as comment from Post p left join p.comments c ")
    List<PostWithCommentsProjection> findAllByProjecting(Pageable pageable);

    @EntityGraph(attributePaths = "comments")
    @Query("select p from Post p")
    List<Post> findAllAndThenMap(Pageable pageable);

}

@Service
class TestService {
    private final PostRepository repository;

    TestService(PostRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void addBaseData() {
        var imgSize = new Random().nextInt(10000, 1000000);

        repository.save(new Post().setTitle("High-Performance Java Persistence")
                .setImage(new byte[imgSize])
                .addComment(new PostComment().setComment("Best book on JPA and Hibernate!").setImage(new byte[imgSize]))
                .addComment(new PostComment().setComment("A must-read for every Java developer!")));
        repository.save(
                new Post().setTitle("Hypersistence Optimizer").setImage(new byte[imgSize])
                        .addComment(new PostComment().setComment("It's like pair programming with Vlad!")));
    }


    public void compareQueryMethods() {
        // Warm up
        for (int i = 0; i < 10; i++) {
            repository.findAllByProjecting(PageRequest.of(0, 10));
            repository.findAllAndThenMap(PageRequest.of(0, 10));
        }

        var pageSize = List.of(10, 50, 100);
        var projectionTime = new ArrayList<Long>();
        var fullQuery = new ArrayList<Long>();



        for (Integer size : pageSize) {
            // Benchmark projection
            long startTime = System.nanoTime();
            var projectionResults = repository.findAllByProjecting(PageRequest.of(0, size));
            projectionTime.add(System.nanoTime() - startTime);

            // Benchmark after mapping
            startTime = System.nanoTime();
            var entities = repository.findAllAndThenMap(PageRequest.of(0, size));
            fullQuery.add(System.nanoTime() - startTime);
        }
        for (int i = 0; i < 3; i++) {

            System.out.println("Page size: " + pageSize.get(i));
            System.out.println("Projection time: " + projectionTime.get(i) / 1_000_000 + " ms");
            System.out.println("After mapping time: " + fullQuery.get(i) / 1_000_000 + " ms");
        }

    }

}


@Accessors(chain = true)
@Getter
@Setter
@Entity(name = "Post")
@Table(name = "post")
class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @CreationTimestamp
    private Instant created;

    private byte[] image;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostComment> comments = new ArrayList<>();

    public Post addComment(PostComment comment) {
        comments.add(comment);
        comment.setPost(this);
        return this;
    }
}

@Accessors(chain = true)
@Getter
@Setter
@Entity
@Table(name = "comment")
class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    private byte[] image;

    private String comment;
}

interface PostWithCommentsProjection {
    Long getId();

    String getTitle();

    List<CommentProjection> getComments();

    interface CommentProjection {
        String getComment();
    }


}


record PostWithCommentsDTO(Long id, String title, List<CommentDTO> comments) {
    public record CommentDTO(String comment) {
    }
}

