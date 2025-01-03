package com.fdifrison.entityrelashionship.one2many.unidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import com.fdifrison.entityrelashionship.utils.Printer;
import jakarta.persistence.*;
import java.util.*;
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
public class o2mParentSide {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2mParentSide.class)
                .profiles(Profiles.Active.one2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner deleteFromList(TestService testService) {
        return args -> {
            var post = testService.savePostWithCommentsInCommentList();
            Printer.focus("Deleting from List mapping");
            testService.removeCommentFromList(post.id());
        };
    }

    @Order(1)
    @Bean
    CommandLineRunner deleteFirstFromListWithOrderColumn(TestService testService) {
        return args -> {
            var post = testService.savePostWithCommentsInCommentListWithOrder();
            Printer.focus("Deleting first element from List mapping with order column");
            testService.removeFirstCommentFromListWithOrder(post.id());
        };
    }

    @Order(2)
    @Bean
    CommandLineRunner deleteLastFromListWithOrderColumn(TestService testService) {
        return args -> {
            var post = testService.savePostWithCommentsInCommentListWithOrder();
            Printer.focus("Deleting last element from List mapping with order column");
            testService.removeLastCommentFromListWithOrder(post.id());
        };
    }

    @Order(3)
    @Bean
    CommandLineRunner runnerSet(TestService testService) {
        return args -> {
            var post = testService.savePostWithCommentsInCommentSet();
            Printer.focus("Deleting last element from Set mapping");
            testService.removeLastCommentFromSet(post.id());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = Post_.COMMENT_LIST)
    Optional<Post> findWithCommentsListById(long id);

    @EntityGraph(attributePaths = Post_.COMMENT_LIST_WITH_ORDER)
    Optional<Post> findWithCommentsListWithOrderById(long id);

    @EntityGraph(attributePaths = Post_.COMMENTS_SET)
    Optional<Post> findWithCommentsSetById(long id);
}

@Service
class TestService {

    private final PostRepository postRepository;

    TestService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * @apiNote 1 SAVE for post + n SAVE for comments + n SAVE for the join table
     */
    @Transactional
    public Post savePostWithCommentsInCommentList() {
        var post = postRepository.save(new Post().withTitle("a new post"));
        post.commentList().add(new Comment().withComment("First comment"));
        post.commentList().add(new Comment().withComment("Second comment"));
        post.commentList().add(new Comment().withComment("Third comment"));
        return post;
    }

    /**
     * @apiNote 1 SAVE for post + n SAVE for comments + n SAVE for the join table
     */
    @Transactional
    public Post savePostWithCommentsInCommentListWithOrder() {
        var post = postRepository.save(new Post().withTitle("a new post"));
        post.commentListWithOrder().add(new Comment().withComment("First comment"));
        post.commentListWithOrder().add(new Comment().withComment("Second comment"));
        post.commentListWithOrder().add(new Comment().withComment("Third comment"));
        return post;
    }

    /**
     * @apiNote 1 SAVE for post + n SAVE for comments + n SAVE for the join table
     */
    @Transactional
    public Post savePostWithCommentsInCommentSet() {
        var post = postRepository.save(new Post().withTitle("a new post"));
        post.commentsSet().add(new Comment().withComment("First comment"));
        post.commentsSet().add(new Comment().withComment("Second comment"));
        post.commentsSet().add(new Comment().withComment("Third comment"));
        return post;
    }

    /**
     * @apiNote 1 DELETE (all) for join table + (n-1) (re)insert for join table + 1 DELETE for comment (due to the orphan removal)
     * @implNote Since the List is an unordered collection, hibernate will first remove all the rows in the join table
     * associated with the post_id, then it will re-add all but the one associated with the comment we are removing and,
     * at last, since we set the orphanRemoval=true, a delete statement on the comment table
     */
    @Transactional
    public void removeCommentFromList(long postId) {
        var post = postRepository.findWithCommentsListById(postId).orElseThrow();
        post.commentList().removeLast();
    }

    /**
     * @apiNote 1 DELETE for join table + (n) update for join table + 1 DELETE for comment (due to the orphan removal)
     * @implNote since a natural ordering is present in the join table (insertion_order) we can specify a @OrderColumn
     * in the collection, this will allow hibernate to target the specific row to delete, but to preserver the ordering,
     * if the element we want to delete is not the last, all the following rows will be updated to fill the gap
     */
    @Transactional
    public void removeFirstCommentFromListWithOrder(long postId) {
        var post = postRepository.findWithCommentsListWithOrderById(postId).orElseThrow();
        post.commentListWithOrder().removeFirst();
    }

    /**
     * @apiNote 1 DELETE for join table + 1 DELETE for comment (due to the orphan removal)
     * @implNote since a natural ordering is present in the join table (insertion_order) we can specify a @OrderColumn
     * in the collection, this will allow hibernate to target the specific row to delete, and since the element we are
     * removing is the last one, the statement triggered are the same for a SET collection
     */
    @Transactional
    public void removeLastCommentFromListWithOrder(long postId) {
        var post = postRepository.findWithCommentsListWithOrderById(postId).orElseThrow();
        post.commentListWithOrder().removeLast();
    }

    /**
     * @apiNote 1 DELETE for join table + 1 DELETE for comment
     */
    @Transactional
    public void removeLastCommentFromSet(long postId) {
        var post = postRepository.findWithCommentsSetById(postId).orElseThrow();
        var last = post.commentsSet().stream()
                .max(Comparator.comparingLong(Comment::id))
                .orElseThrow();
        post.commentsSet().remove(last);
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
    @JoinTable(
            name = "post_comment",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "comment_id"))
    private List<Comment> commentList = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "post_comment",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "comment_id"))
    @OrderColumn(name = "insertion_order")
    private List<Comment> commentListWithOrder = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "post_comment",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "comment_id"))
    private Set<Comment> commentsSet = new HashSet<>();
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
}
