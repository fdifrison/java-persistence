package com.fdifrison.entityrelashionship.one2one.bidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import com.fdifrison.entityrelashionship.utils.Printer;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.Accessors;
import org.hibernate.annotations.*;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class o2oMapsIdBidirectional {

    public static void main(String[] args) {
        new SpringApplicationBuilder(o2oMapsIdBidirectional.class)
                .profiles(Profiles.Active.one2one.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner runner(PostService postService, PostRepository postRepository) {
        return args -> {
            var post = postService.savePost();
            postService.saveDetail(post.id());
            Printer.Focus("FetchType.Lazy doesn't work, two select query executed");
            var byId = postRepository.findById(post.id()).orElseThrow();
            System.out.println(byId);
        };
    }

    @Order(1)
    @Bean
    CommandLineRunner nPlusOne(PostService postService, PostRepository postRepository) {
        return args -> {
            for (int i = 0; i < 3; i++) {
                var post = postService.savePost();
                postService.saveDetail(post.id());
            }

            Printer.Focus("Proxy works from the child side");
            var detailWithMapsIdWhereCreatedByIn = postService.findDetailWithMapsIdWhereCreatedByIn("Pot%");
            System.out.println(detailWithMapsIdWhereCreatedByIn);

            Printer.Focus("N+1 query executed!");
            var postsWhereTitleIn = postService.findPostsWhereTitleIn("Tit%");
            System.out.println(postsWhereTitleIn);
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {

    @Query(
            value = """
            select * from post p
            where p.title like :title
            """,
            nativeQuery = true)
    List<Post> findPostsWhereTitleIn(@Param("title") String title);
}

@Repository
interface DetailRepository extends JpaRepository<DetailWithMapsId, Long> {

    @Query(
            value =
                    """
            select * from detail_with_maps_id d
            where d.created_by like :createdBy
            """,
            nativeQuery = true)
    List<DetailWithMapsId> findDetailWithMapsIdWhereCreatedByIn(@Param("createdBy") String createdBy);
}

@Service
class PostService {

    private final PostRepository postRepository;
    private final DetailRepository detailRepository;

    PostService(PostRepository postRepository, DetailRepository detailRepository) {
        this.postRepository = postRepository;
        this.detailRepository = detailRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post().withTitle("Title"));
    }

    @Transactional
    public void saveDetail(long postId) {
        var post = postRepository.findById(postId).orElseThrow();
        Printer.Focus();
        post.setDetail(new DetailWithMapsId().withCreatedBy("Potter"));
    }

    /**
     * @apiNote N+1 SELECT where n is the number of posts retrieved
     * @implNote since the parent side of the relationship isn't able to proxy the child entity and hibernate needs to
     * know if assign a null value or an object to the one-to-one mapping, a select query is performed for each post
     * entity retrieved in order to check, and eventually find, the associated detail
     */
    public List<Post> findPostsWhereTitleIn(String title) {
        return postRepository.findPostsWhereTitleIn(title);
    }

    /**
     * @apiNote 1 SELECT is executed
     * @implNote from the child side the proxy of the one-to-one relation works and therefore the associated posts
     * entities are not fetched if not explicitly requested (N.B. in turn a lazyInitializationException will be thrown
     * if the post entity is requested)
     */
    public List<DetailWithMapsId> findDetailWithMapsIdWhereCreatedByIn(String createdBy) {
        return detailRepository.findDetailWithMapsIdWhereCreatedByIn(createdBy);
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

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // TODO fetch type lazy won't work because hibernate needs to know if it shall initialize the detail attribute with
    //  null or a proxy class. It can only find that out, by querying the detail table to find a record that references
    //  this Post entity.
    private DetailWithMapsId detail;

    public void setDetail(DetailWithMapsId detail) {
        this.detail = detail;
        detail.post(this);
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
@Entity
@Table(name = "detail_with_maps_id")
class DetailWithMapsId {

    @Id
    private long postId;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdOn;

    private @With String createdBy;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "post_id")
    private @With Post post;

    @Override
    public String toString() {
        return "DetailWithMapsId{" + "postId=" + postId + ", createdOn=" + createdOn + '}';
    }
}
