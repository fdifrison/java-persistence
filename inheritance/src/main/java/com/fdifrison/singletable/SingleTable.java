package com.fdifrison.singletable;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SingleTable {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SingleTable.class)
                .profiles(Profiles.Active.single_table.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService service) {
        return args -> {
            var board = service.boardRepository.save(new Board().name("Spring"));
            Printer.focus();
            var post = service.createPost(board.id());
            Printer.entity(post);
        };
    }
}

interface BoardRepository extends JpaRepository<Board, Long> {}

interface PostRepository extends JpaRepository<Post, Long> {}

interface AnnouncementRepository extends JpaRepository<Announcement, Long> {}

interface TopicRepository extends JpaRepository<Topic, Long> {}

@Service
class TestService {

    public final BoardRepository boardRepository;
    public final PostRepository postRepository;
    public final AnnouncementRepository announcementRepository;
    public final TopicRepository topicRepository;

    TestService(
            BoardRepository boardRepository,
            PostRepository postRepository,
            AnnouncementRepository announcementRepository,
            TopicRepository topicRepository) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.announcementRepository = announcementRepository;
        this.topicRepository = topicRepository;
    }

    public Post createPost(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        var post = new Post();
        post.setOwner("fdifrison");
        post.setTitle("Java Persistence");
        post.setContent("Learning from Vlad");
        post.setBoard(board);
        return postRepository.save(post);
    }
}

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@Entity
@Table
class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = Topic_.BOARD)
    private List<Topic> topics = new ArrayList<>();
}

@Getter
@Setter
@Entity
@Table
@Inheritance(strategy = InheritanceType.SINGLE_TABLE) // default inheritance type
// TODO we are using single table inheritance, meaning that the Topic table is used to represents all its polymorphic
//  representations (Post and Announcement); this require the Topic to have the fields required by both the sub-entities
class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String owner;

    @CreationTimestamp
    private Instant createdOn;

    @ManyToOne(fetch = FetchType.LAZY)
    private Board board;
}

@Setter
@Getter
@Entity
class Post extends Topic {
    private String content;
}

@Getter
@Setter
@Entity
class Announcement extends Topic {
    private Instant validUntil;
}

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@Entity
@Table
class TopicStatistics {

    @Id
    private Long topicId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    private Topic topic;

    private long views;
}
