package com.fdifrison.entityrelashionship.many2many.unidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import com.fdifrison.entityrelashionship.utils.Printer;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class m2mUnidirectional {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2mUnidirectional.class)
                .profiles(Profiles.Active.many2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {
            Printer.focus("Inserting a post with tags");
            var post = new Post()
                    .withTitle("First Post")
                    .addTag(new Tag().withName("Java"))
                    .addTag(new Tag().withName("Spring"));
            var savePost = testService.savePost(post);
            Printer.entity(savePost);

            Printer.focus("Dropping a Tag");
            var droppedTag = testService.dropTag(
                    savePost.id(),
                    savePost.tags().stream()
                            .filter(tag -> tag.name().equals("Java"))
                            .findFirst()
                            .orElseThrow());
            Printer.entity(droppedTag);
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = Post_.TAGS)
    Optional<Post> findWithTagsById(Long aLong);
}

@Repository
interface TagRepository extends JpaRepository<Tag, Long> {}

@Service
class TestService {

    private final PostRepository postRepository;

    TestService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * @apiNote 1 INSERT for the post + n INSERT for the tags (if new) + m INSERT for the join table records
     * @implNote since the Post entity is holding the relationship, persisting a post will also produce the insert
     * statements for all the newly created tags and for the join table records associating tags and post ids
     */
    public Post savePost(Post post) {
        return postRepository.save(post);
    }

    @Transactional
    public Post dropTag(long postId, Tag tag) {
        var post = postRepository.findWithTagsById(postId).orElseThrow();
        return post.removeTag(tag);
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

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    // TODO cascade DELETE will affect both the join table and the other end of the relation that by definition should
    //  be independent from the other side in a many-to-many relation
    @JoinTable(
            name = "post_tag",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    public Post addTag(Tag tag) {
        tags.add(tag);
        return this;
    }

    public Post removeTag(Tag tag) {
        tags.remove(tag);
        return this;
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "tag")
class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private @With String name;
}
