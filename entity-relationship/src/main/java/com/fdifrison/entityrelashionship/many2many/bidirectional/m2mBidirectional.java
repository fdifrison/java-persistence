package com.fdifrison.entityrelashionship.many2many.bidirectional;

import com.fdifrison.entityrelashionship.configurations.Profiles;
import jakarta.persistence.*;
import java.util.HashSet;
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@SpringBootApplication
@ConfigurationPropertiesScan
public class m2mBidirectional {

    public static void main(String[] args) {
        new SpringApplicationBuilder(m2mBidirectional.class)
                .profiles(Profiles.Active.many2many.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Order(0)
    @Bean
    CommandLineRunner runner(TestService testService) {
        return args -> {};
    }
}

@Repository
interface PostRepository extends JpaRepository<Post, Long> {}

@Repository
interface TagRepository extends JpaRepository<Tag, Long> {}

@Service
class TestService {}

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

    public void addTag(Tag tag) {
        tags.add(tag);
        tag.posts().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.posts().remove(this);
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

    @ManyToMany(mappedBy = Post_.TAGS)
    private Set<Post> posts = new HashSet<>();
}
