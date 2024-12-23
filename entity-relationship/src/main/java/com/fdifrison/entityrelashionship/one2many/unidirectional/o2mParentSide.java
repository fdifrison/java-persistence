package com.fdifrison.entityrelashionship.one2many.unidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2mParentSide {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2mParentSide.class)
                .profiles(Profiles.Active.one2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {
            var post = postService.savePost();
            var comment = postService.addComment();
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Repository
interface PostCommentRepository extends JpaRepository<Comment, Long> {}

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

    @OneToMany
    @JoinColumn(name = "post_id", nullable = false)
    private List<Comment> comments = new ArrayList<>();
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
}
