package com.fdifrison.mapped_superclass;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MappedSuperclass {

    public static void main(String[] args) {
        new SpringApplicationBuilder(MappedSuperclass.class)
                .profiles(Profiles.Active.mapped_superclass.name())
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(TestService service) {
        return args -> {
            var board = service.creatBoard(new Board().name("Spring"));

            Printer.focus("Creating topics...");
            var post = service.createPost(board.id());
            var announcement = service.createAnnouncement(board.id());
            Printer.entity(post);
            Printer.entity(announcement);

            Printer.focus("Creating PostStatistics..");
            var postStatistics = service.createPostStatistics(post.getId());
            Printer.entity(postStatistics);
        };
    }
}

interface BoardRepository extends JpaRepository<Board, Long> {}

interface PostRepository extends JpaRepository<Post, Long> {
    @EntityGraph(attributePaths = Post_.BOARD)
    @Override
    Optional<Post> findById(Long aLong);
}

interface AnnouncementRepository extends JpaRepository<Announcement, Long> {}

interface PostStatisticsRepository extends JpaRepository<PostStatistics, Long> {}

@Service
class TestService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final AnnouncementRepository announcementRepository;
    private final PostStatisticsRepository postStatisticsRepository;

    TestService(
            BoardRepository boardRepository,
            PostRepository postRepository,
            AnnouncementRepository announcementRepository,
            PostStatisticsRepository postStatisticsRepository) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.announcementRepository = announcementRepository;
        this.postStatisticsRepository = postStatisticsRepository;
    }

    public Board creatBoard(Board board) {
        return boardRepository.save(board);
    }

    @Transactional
    // TODO @Transactional not required since board eagerly fetch topics, but better to have it
    public Post createPost(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        var post = new Post()
                .setOwner("fdifrison")
                .setTitle("Java Persistence")
                .setContent("Learning from Vlad")
                .setBoard(board);
        return createPost(post);
    }

    /**
     * @apiNote 1 INSERT for the child table post
     */
    private Post createPost(Post post) {
        return postRepository.save(post);
    }

    @Transactional
    // TODO @Transactional not required since board eagerly fetch topics, but better to have it
    public Announcement createAnnouncement(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        var announcement = new Announcement()
                .setOwner("fdifrison")
                .setTitle("Time to study!")
                .setValidUntil(Instant.now().plus(Duration.ofDays(1)))
                .setBoard(board);
        return createAnnouncement(announcement);
    }

    /**
     * @apiNote 1 INSERT for the child table announcement
     */
    private Announcement createAnnouncement(Announcement announcement) {
        return announcementRepository.save(announcement);
    }

    /**
     * @apiNote 1 INSERT for the table postStatistics
     */
    @Transactional
    public PostStatistics createPostStatistics(Long postId) {
        var post = postRepository.findById(postId).orElseThrow();
        var postStatistics = new PostStatistics().topic(post);
        return postStatisticsRepository.save(postStatistics);
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

    @Override
    public String toString() {
        return "Board{" + "name='" + name + '\'' + '}';
    }
}

@Getter
@jakarta.persistence.MappedSuperclass // default inheritance type
abstract class Topic<T extends Topic<T>> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String owner;

    @CreationTimestamp
    private Instant createdOn;

    @ManyToOne(fetch = FetchType.LAZY)
    private Board board;

    public T setTitle(String title) {
        this.title = title;
        return (T) this;
    }

    public T setOwner(String owner) {
        this.owner = owner;
        return (T) this;
    }

    public T setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
        return (T) this;
    }

    public T setBoard(Board board) {
        this.board = board;
        return (T) this;
    }
}

@Getter
@Entity
@Table(name = "post")
class Post extends Topic<Post> {
    private String content;

    @Override
    public String toString() {
        return "Post{" + "id="
                + super.getId() + ", title='"
                + super.getTitle() + '\'' + ", owner='"
                + super.getOwner() + '\'' + ", createdOn="
                + super.getCreatedOn() + ", board="
                + super.getBoard() + ", content='"
                + content + '\'' + '}';
    }

    public Post setContent(String content) {
        this.content = content;
        return this;
    }
}

@Getter
@Entity
@Table(name = "announcement")
class Announcement extends Topic<Announcement> {
    private Instant validUntil;

    @Override
    public String toString() {
        return "Announcement{" + "id="
                + super.getId() + ", title='"
                + super.getTitle() + '\'' + ", owner='"
                + super.getOwner() + '\'' + ", createdOn="
                + super.getCreatedOn() + ", board="
                + super.getBoard() + ", validUntil="
                + validUntil + '}';
    }

    public Announcement setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
        return this;
    }
}

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@jakarta.persistence.MappedSuperclass
abstract class TopicStatistics<T extends Topic> {

    @Id
    private Long id;

    private long views;

    public TopicStatistics<T> incrementViews() {
        this.views++;
        return this;
    }
}

@Entity
@Table
@Getter
@Setter
@Accessors(fluent = true, chain = true)
class PostStatistics extends TopicStatistics<Post> {

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = TopicStatistics_.ID)
    private Post topic;

    @Override
    public String toString() {
        return "PostStatistics{" + "id=" + super.id() + ", topic=" + topic + ", views=" + super.views() + '}';
    }
}

@Entity
@Table
@Getter
@Setter
class AnnouncementStatistics extends TopicStatistics<Announcement> {

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = TopicStatistics_.ID)
    private Announcement topic;

    @Override
    public String toString() {
        return "AnnouncementStatistics{" + "id=" + super.id() + ", topic=" + topic + ", views=" + super.views() + '}';
    }
}
