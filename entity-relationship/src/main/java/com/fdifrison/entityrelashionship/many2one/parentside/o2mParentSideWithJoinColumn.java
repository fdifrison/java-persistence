package com.fdifrison.entityrelashionship.many2one.parentside;

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
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2mParentSideWithJoinColumn {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2mParentSideWithJoinColumn.class)
                .profiles(Profiles.Active.many2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Repository
interface PostCommentRepository extends JpaRepository<Comment, Long> {}

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

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = Comment_.POST_ID, nullable = false)
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
    private long id;

    private @With String comment;

    @Column(name = "post_id")
    private Long postId; // TODO lets see if there is a name clash
}
