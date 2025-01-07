package com.fdifrison.many2one.childside;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class m2oChildSide {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2oChildSide.class)
                .profiles(Profiles.Active.many2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {
            var post = testService.savePost();
            var comment = testService.addComment();

            Printer.focus("Add comment to post");
            testService.linkCommentToPost(comment.id(), post);

            Printer.focus("Remove comment from post");
            testService.findCommentAndSetPostReferenceToNull(comment.id());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Repository
interface CommentRepository extends JpaRepository<Comment, Long> {}

@Service
class TestService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    TestService(PostRepository postRepository, CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post().withTitle("a new post"));
    }

    public Comment addComment() {
        var postComment = new Comment().withComment("a comment");
        return commentRepository.save(postComment);
    }

    /**
     * @implNote since @Transactional is active and since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to the already persisted Post object hibernate will execute the update statement
     * at flush time
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void linkCommentToPost(long postId, Post post) {
        var comment = commentRepository.findById(postId).orElseThrow();
        comment.post(post);
    }

    /**
     * @apiNote no query executed
     * @implNote since the comment entity is not part of the transaction setting the reference to the post won't fire
     * any update statement
     */
    @Transactional
    public void linkCommentToPostWontUpdate(Comment comment, Post post) {
        comment.post(post);
    }

    /**
     * @implNote since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to null hibernate will execute the update statement at flush time
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void findCommentAndSetPostReferenceToNull(long id) {
        var comment = commentRepository.findById(id).orElseThrow();
        comment.post(null);
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
@Entity
@Table(name = "comment")
class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private @With String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    //  TODO @JoinColumn could be omitted since by default hibernate join the name of the identity attribute with
    //   identifier using an underscore
    @JoinColumn(name = "post_id")
    private Post post;
}
