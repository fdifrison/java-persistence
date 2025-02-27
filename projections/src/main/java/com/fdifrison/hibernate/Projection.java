package com.fdifrison.hibernate;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Projection {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Projection.class)
                .profiles(Profiles.Active.projection.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService service) {
        return args -> {
            service.addBaseData();
            var list = service.JPQLTupleMappingWithJPA();
            Printer.entityList(list);
        };
    }
}

@Service
class TestService {
    private final EntityManager em;

    TestService(EntityManagerFactory emFactory) {
        em = emFactory.createEntityManager();
    }

    public void addBaseData() {
        em.getTransaction().begin();
        em.persist(new Post().setTitle("High-Performance Java Persistence")
                .addComment(new PostComment().setComment("Best book on JPA and Hibernate!"))
                .addComment(new PostComment().setComment("A must-read for every Java developer!")));
        em.persist(
                new Post().setTitle("Hypersistence Optimizer")
                        .addComment(new PostComment().setComment("It's like pair programming with Vlad!")));
        em.getTransaction().commit();
    }

    // Modern approach using Hibernate 6's type-safe APIs
    public List<PostDTOWithComment> JPQLTupleTransformer() {
        em.getTransaction().begin();

        // Use explicit typing with a select statement returning Object[]
        Query<Object[]> query = em.createQuery("""
                            select p.id as id,
                                   p.title as title,
                                   pc.comment as comment
                            from Post p
                            join PostComment pc on pc.post.id = p.id
                            order by p.id
                        """, Object[].class)
                .unwrap(Query.class);

        // Process the raw results with mapping function instead of using transformers
        Map<Long, PostDTOWithComment> postDTOMap = new LinkedHashMap<>();

        List<Object[]> results = query.getResultList();
        for (Object[] tuple : results) {
            Long id = (Long) tuple[0];
            String title = (String) tuple[1];
            String comment = (String) tuple[2];

            PostDTOWithComment dto = postDTOMap.computeIfAbsent(
                    id,
                    key -> new PostDTOWithComment(id, title)
            );

            dto.comments().add(new PostDTOWithComment.CommentDTO(comment));
        }

        return new ArrayList<>(postDTOMap.values());
    }

    // Alternative using JPA's Tuple API - also type-safe
    public List<PostDTOWithComment> JPQLTupleMappingWithJPA() {
        em.getTransaction().begin();

        // Use JPA's native Tuple support
        List<Tuple> tuples = em.createQuery("""
                        select p.id as id,
                               p.title as title,
                               pc.comment as comment
                        from Post p
                        join PostComment pc on pc.post.id = p.id
                        order by p.id
                        """, Tuple.class)
                .getResultList();

        // Process the tuples into DTOs
        Map<Long, PostDTOWithComment> postDTOMap = new LinkedHashMap<>();

        for (Tuple tuple : tuples) {
            Long id = tuple.get("id", Long.class);
            String title = tuple.get("title", String.class);
            String comment = tuple.get("comment", String.class);

            PostDTOWithComment dto = postDTOMap.computeIfAbsent(
                    id,
                    key -> new PostDTOWithComment(id, title)
            );

            dto.comments().add(new PostDTOWithComment.CommentDTO(comment));
        }

        return new ArrayList<>(postDTOMap.values());
    }

    // Using constructor expressions (most type-safe approach)
    public List<PostDTO> usingConstructorExpression() {
        em.getTransaction().begin();

        return em.createQuery("""
                        select new com.fdifrison.hibernate.PostDTO(p.id, p.title)
                        from Post p
                        order by p.id
                        """, PostDTO.class)
                .getResultList();
    }

    // Native query with explicit mapping (for complex cases)
    public List<PostDTOWithComment> nativeQueryWithMapping() {
        em.getTransaction().begin();

        NativeQuery<Map> nativeQuery = em.createNativeQuery("""
                        SELECT p.id AS id,
                               p.title AS title,
                               pc.comment AS comment
                        FROM post p
                        JOIN comment pc ON pc.post_id = p.id
                        ORDER BY p.id
                        """)
                .unwrap(NativeQuery.class)
                .setTupleTransformer((tuple, aliases) -> {
                    Map<String, Object> result = new HashMap<>();
                    for (int i = 0; i < aliases.length; i++) {
                        result.put(aliases[i], tuple[i]);
                    }
                    return result;
                });

        List<Map> results = nativeQuery.getResultList();
        Map<Long, PostDTOWithComment> dtoMap = new LinkedHashMap<>();

        for (Map row : results) {
            Long id = ((Number) row.get("id")).longValue();
            String title = (String) row.get("title");
            String comment = (String) row.get("comment");

            PostDTOWithComment dto = dtoMap.computeIfAbsent(
                    id,
                    key -> new PostDTOWithComment(id, title)
            );

            dto.comments().add(new PostDTOWithComment.CommentDTO(comment));
        }

        return new ArrayList<>(dtoMap.values());
    }
}

@Accessors(chain = true)
@Getter
@Setter
@Entity(name = "Post")
@Table(name = "post")
class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @CreationTimestamp
    private Instant created;

    private byte[] image;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostComment> comments = new ArrayList<>();

    public Post addComment(PostComment comment) {
        comments.add(comment);
        comment.setPost(this);
        return this;
    }
}

@Accessors(chain = true)
@Getter
@Setter
@Entity
@Table(name = "comment")
class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;

    private String comment;

    private byte[] image;
}

record PostDTO(long id, String title) {
}

record PostDTOWithComment(long id, String title, List<CommentDTO> comments) {

    public PostDTOWithComment(long id, String title) {
        this(id, title, new ArrayList<>());
    }

    record CommentDTO(String comment) {
    }
}