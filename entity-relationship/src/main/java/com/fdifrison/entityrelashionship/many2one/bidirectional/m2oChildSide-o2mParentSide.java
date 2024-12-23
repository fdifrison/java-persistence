package com.fdifrison.entityrelashionship.many2one.bidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.*;
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
class m2oBidirectional {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2oBidirectional.class)
                .profiles(Profiles.Active.many2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService, PostRepository postRepository) {
        return args -> {
            Post postState;
            var post = postService.savePostWithComments();
            postState = postRepository.findWithCommentsById(post.id()).orElseThrow();
            System.out.println(postState);
            var updatedPost = postService.removeLastComment(postState.id());
            postState = postRepository.findWithCommentsById(updatedPost.id()).orElseThrow();
            System.out.println(postState);
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = Post_.COMMENTS)
    Optional<Post> findWithCommentsById(long id);
}

@Repository
interface PostCommentRepository extends JpaRepository<Comment, Long> {}

@Service
class PostService {

    private final PostRepository postRepository;

    PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * @apiNote 3 INSERT statement are executed
     * @implNote On the parent side the @OneToMany annotation pointing to the comment collections must
     * be specified with cascade = CascadeType.ALL otherwise the comments won't be inserted. The Post entity is enhanced
     * with the utility method .addComment() which sync the parent with the children, setting the post_id on the latter
     * with comment.post(this)
     */
    @Transactional
    public Post savePostWithComments() {
        var post = postRepository.save(new Post().withTitle("a new post"));
        post.addComment(new Comment().withComment("first comment"));
        post.addComment(new Comment().withComment("second comment"));
        return post;
    }

    /**
     * @apiNote 1 UPDATE for Post 1 DELETE for Comment is executed
     * @implNote On the parent side the @OneToMany annotation pointing to the comment collections must have the option
     *  "orphanRemoval=true" otherwise the Post will be updated removing the Comment from its collection but the
     *  comment itself will remain orphan and the delete statement not triggered.
     */
    @Transactional
    public Post removeLastComment(long postId) {
        var post = postRepository.findWithCommentsById(postId).orElseThrow();
        post.removeComment(post.comments().getLast());
        return post;
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

    @OneToMany(
            mappedBy = Comment_.POST,
            cascade = CascadeType.ALL, // TODO required to enable the parent-child synchronization
            orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    // TODO Even if the child-side is responsible for synchronizing the association
    //  state changes with the database, a bidirectional association must
    //  always have both sides in sync

    public void addComment(Comment comment) {
        comments.add(comment);
        // TODO required to set the post_id on the Comment side (child), otherwise, when retrieving the Post entity the
        //  related comments won't be linked
        comment.post(this);
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        // TODO this is not necessary for comment deletion since orphan removal will take care of that, but it is a good
        //  practice to let the GC free some heap space
        comment.post(null);
    }

    @Override
    public String toString() {
        return "Post{" + "id="
                + id + ", title='"
                + title + '\'' + ", comments=["
                + comments.stream().map(Comment::comment).collect(Collectors.joining(",")) + "]}";
    }
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
    private Post post;
}
