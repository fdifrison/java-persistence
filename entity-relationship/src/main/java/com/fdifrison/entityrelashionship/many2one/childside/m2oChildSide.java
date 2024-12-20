package com.fdifrison.entityrelashionship.many2one.childside;

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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@ConfigurationPropertiesScan
public class m2oChildSide {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2oChildSide.class)
                .profiles(Profiles.Active.many_to_one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {
            var post = postService.savePost();
            var comment = postService.addComment();
            postService.linkCommentToPost(comment.id(), post);
            postService.findCommentAndSetPostReferenceToNull(comment.id());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {
}

@Repository
interface PostCommentRepository extends JpaRepository<Comment, Long> {
}

@Service
class PostService {


    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    PostService(PostRepository postRepository, PostCommentRepository postCommentRepository) {
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post().withTitle("a new post"));
    }

    public Comment addComment() {
        var postComment = new Comment().withComment("a comment");
        return postCommentRepository.save(postComment);
    }

    /**
     * @implNote since @Transactional is active and since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to the already persisted Post object hibernate will execute the update statement
     * at flush time
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void linkCommentToPost(long postId, Post post) {
        var comment = postCommentRepository.findById(postId).orElseThrow();
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
     * @implNote since @Transactional is active and since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to null hibernate will execute the update statement at flush time
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void findCommentAndSetPostReferenceToNull(long id) {
        var comment = postCommentRepository.findById(id).orElseThrow();
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
    private long id;

    @Column(nullable = false)
    private @With String title;

}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "post_comment")
class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private @With String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    //  TODO Could be omitted since by default hibernate join the name of the identity attribute with identifier
    //   using an underscore
    @JoinColumn(name = "post_id")
    private Post post;
}