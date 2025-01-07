package com.fdifrison.many2one.parentside;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2mParentSideWithJoinColumn {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2mParentSideWithJoinColumn.class)
                .profiles(Profiles.Active.many2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {
            Printer.focus("Inefficient mapping");
            var post = testService.savePostWithComment();
            testService.deleteLastComment(post.id());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = Post_.COMMENTS)
    Optional<Post> findWithCommentsById(long id);
}

@Service
class TestService {

    private final PostRepository postRepository;

    TestService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * @apiNote 1 INSERT for post + n insert for COMMENT + n UPDATE for comment
     * @implNote since the child entity is flushed before the parent one-to-many collection, hibernate has no clue on
     * the value of the foreign key, therefore, an update is required for each entity to set the post_id value once is
     * known. If nullable=true is set, the comments are flushed with the foreign key populated even the first time but
     * the update statements are executed anyway.
     */
    @Transactional
    public Post savePostWithComment() {
        var post = postRepository.save(new Post().withTitle("Post Title"));
        post.comments().add(new Comment().withComment("First comment"));
        post.comments().add(new Comment().withComment("Second comment"));
        post.comments().add(new Comment().withComment("Third comment"));
        return post;
    }

    /**
     * @apiNote 1 UPDATE + 1 DELETE or 1 DELETE (if nullable=false is set)
     * @implNote if nullable=false is not set a useless UPDATE statement is executed to set the post_id to null in the
     * comment, the same that will be deleted next; this is required by the unidirectional one-to-many mapping on the
     * parent-side
     */
    @Transactional
    public void deleteLastComment(long postId) {
        var post = postRepository.findWithCommentsById(postId).orElseThrow();
        post.comments().removeLast();
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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = Comment_.POST_ID, nullable = false)
    // TODO nullable=false is required so that a delete of a comment doesn't trigger two statements (1 UPDATE + 1
    // DELETE)
    //  but only 1 delete
    private List<Comment> comments = new ArrayList<>();
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "comment")
class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private @With String comment;

    @Column(name = "post_id", insertable = false, updatable = false)
    private Long post_id;
}
