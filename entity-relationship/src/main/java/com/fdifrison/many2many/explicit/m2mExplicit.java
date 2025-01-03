package com.fdifrison.many2many.explicit;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.many2many.bidirectional.Post_;
import com.fdifrison.many2many.bidirectional.Tag_;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;
import org.hibernate.annotations.NaturalId;
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
public class m2mExplicit {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2mExplicit.class)
                .profiles(Profiles.Active.many2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {
            var javaTag = testService.saveTag(new Tag().withName("Java"));
            var springTag = testService.saveTag(new Tag().withName("Spring"));
            var aPost = new Post().withTitle("A post");
            var savedPost = testService.savePostWithTags(aPost, List.of(javaTag.id(), springTag.id()));
            Printer.entity(savedPost);

            Printer.focus("Retrieving Post and remove one tag");
            var droppedTag = testService.dropTag(savedPost.id(), javaTag.id());
            Printer.entity(droppedTag);
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {
    // TODO not required but avoid 2 select instead of 1
    @EntityGraph(attributePaths = {Post_.TAGS + "." + PostTag_.TAG, Post_.TAGS + "." + PostTag_.POST})
    Optional<Post> findWithTagsById(long id);
}

@Repository
interface TagRepository extends JpaRepository<Tag, Long> {
    // TODO not required but avoid 2 select instead of 1
    @EntityGraph(attributePaths = {Tag_.POSTS + "." + PostTag_.POST, Tag_.POSTS + "." + PostTag_.TAG})
    Optional<Tag> findWithPostsById(long id);
}

@Service
class TestService {
    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    TestService(PostRepository postRepository, TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.tagRepository = tagRepository;
    }

    @Transactional
    public Post savePostWithTags(Post post, List<Long> tagsId) {
        tagRepository.findAllById(tagsId).forEach(post::addTag);
        Printer.focus("Persisting a post with tags");
        return postRepository.save(post);
    }

    public Tag saveTag(Tag tag) {
        return tagRepository.save(tag);
    }

    /**
     * @apiNote 2 SELECT (if entity graph enabled) + 1 DELETE from the join table
     * @implNote with the utility method on the Post entity to remove a tag from the join table it is possible to
     * synchronize both sides in one shot, requiring only one delete table even if the underling collection is a List
     * (contrary to what happens in canonical bidirectional one-to-one mapping)
     */
    @Transactional
    public Post dropTag(long postId, Long tagId) {
        var tag = tagRepository.findWithPostsById(tagId).orElseThrow();
        var post = postRepository.findWithTagsById(postId).orElseThrow();
        var entity = post.removeTag(tag);
        return postRepository.save(entity);
    }
}

@Getter
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

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    // TODO the relationship becomes similar to a bidirectional one-to-many and since the two parent entity post and tag
    //  are not referring one another but pointing to the postTag join table, we are able to use the cascade type ALL
    //  and the orphan removal without risks
    private List<PostTag> tags = new ArrayList<>();

    // TODO add and remove utility methods become more complex since they need to synchronize both side of the join
    //  table, nonetheless they are very useful
    public void addTag(Tag tag) {
        PostTag postTag = new PostTag(this, tag);
        tags.add(postTag);
        tag.posts().add(postTag);
    }

    public Post removeTag(Tag tag) {
        tags.stream()
                .filter(t -> t.post().equals(this) && t.tag().equals(tag))
                .findFirst()
                .ifPresent(t -> {
                    tags.remove(t);
                    t.tag().posts().remove(t);
                    t.post(null);
                    t.tag(null);
                });
        return this;
    }

    @Override
    public String toString() {
        return "Post{" + "id=" + id + ", title='" + title + '\'' + ", tags=" + tags + '}';
    }
}

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
@Entity
@Table(name = "tag")
class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId
    @Column(nullable = false)
    private @With String name;

    @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, orphanRemoval = true)
    // TODO the relationship becomes similar to a bidirectional one-to-many and since the two parent entity post and tag
    //  are not referring one another but pointing to the postTag join table, we are able to use the cascade type ALL
    //  and the orphan removal without risks
    private List<PostTag> posts = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Objects.equals(name, tag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Tag{" + "id=" + id + ", name='" + name + '}';
    }
}

@Getter
@Setter
@NoArgsConstructor
@Accessors(fluent = true, chain = true)
@Entity
@Table(name = "post_tag")
class PostTag {

    public PostTag(Post post, Tag tag) {
        this.post = post;
        this.tag = tag;
        this.id = new PostTagId(post().id(), tag.id());
    }

    @EmbeddedId
    private PostTagId id;

    @ManyToOne
    @MapsId(PostTagId_.POST_ID)
    // TODO jpa is telling hibernate that the Post is controlled by the embedded id PostTagId by the variable postId
    private Post post;

    @ManyToOne
    @MapsId(PostTagId_.TAG_ID)
    // TODO jpa is telling hibernate that the Tag is controlled by the embedded id PostTagId by the variable tagId
    private Tag tag;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostTag that = (PostTag) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "PostTag{" + "id=" + id + ", tag=" + tag.name() + '}';
    }
}

@Embeddable
record PostTagId(@Column(name = "post_id") Long postId, @Column(name = "tag_id") Long tagId) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostTagId that = (PostTagId) o;
        return Objects.equals(this.postId, that.postId()) && Objects.equals(this.tagId, that.tagId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.postId, this.tagId);
    }

    @Override
    public String toString() {
        return "PostTagId[" + "postId=" + postId + ", " + "tagId=" + tagId + ']';
    }
}
