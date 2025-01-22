package com.fdifrison.singletable;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            var board = service.creatBoard(new Board().name("Spring"));

            try {
                Printer.focus("Adding check constrain");
                service.addCheckConstrain();
            } catch (SQLException | JDBCException _) {
                System.out.println("Check constrain already exists");
            }

            Printer.focus("Adding check constrain");
            var wrongPost = new Post();
            wrongPost.setOwner("fdifrison");
            wrongPost.setTitle("Java Persistence");
            wrongPost.setContent(null);
            wrongPost.setBoard(board);
            try {
                service.createPost(wrongPost);
            } catch (SQLException | DataIntegrityViolationException _) {

            }


            Printer.focus("Creating topics...");
            var post = service.createPost(board.id());
            var announcement = service.createAnnouncement(board.id());
            Printer.entity(post);
            Printer.entity(announcement);

            Printer.focus("Adding statistics");
            service.addStatistics(post.getId());
            service.addStatistics(announcement.getId());

            Printer.focus("Performing a polymorphic query to retrieve all topics");
            var boardsTopics = service.getBoardsTopics(board.id());
            Printer.entityList(boardsTopics);

            Printer.focus("Finding only posts among topics");
            var allPosts = service.getAllPosts();
            Printer.entityList(allPosts);

            Printer.focus("Finding all topics sorted by dtype");
            var allTopics = service.getAllTopicsSortedByType();
            Printer.entityList(allTopics);
        };
    }
}

interface BoardRepository extends JpaRepository<Board, Long> {
}

interface PostRepository extends JpaRepository<Post, Long> {
}

interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}

interface TopicRepository extends JpaRepository<Topic, Long> {

    @Query(value = """
            select t from Topic t where t.board = :board
            """)
    List<Topic> findTopicsByBoard(@Param("board") Board board);

    @EntityGraph(attributePaths = Topic_.BOARD)
    @Query(value = """
            select t from Topic t order by t.class, t.id desc
            """)
    List<Topic> findTopicsSortedByType();

    @EntityGraph(attributePaths = Topic_.BOARD)
    @Query(value = """
            select p from Post p
            """)
    List<Topic> findAllPosts();

}

interface TopicStatisticsRepository extends JpaRepository<TopicStatistics, Long> {
}

@Service
class TestService {

    private final EntityManager em;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final AnnouncementRepository announcementRepository;
    private final TopicRepository topicRepository;
    private final TopicStatisticsRepository topicStatisticsRepository;


    TestService(
            EntityManager em, BoardRepository boardRepository,
            PostRepository postRepository,
            AnnouncementRepository announcementRepository,
            TopicRepository topicRepository,
            TopicStatisticsRepository topicStatisticsRepository) {
        this.em = em;
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.announcementRepository = announcementRepository;
        this.topicRepository = topicRepository;
        this.topicStatisticsRepository = topicStatisticsRepository;
    }

    public void addCheckConstrain() throws SQLException {
        em.unwrap(Session.class).doWork(
                connection -> {
                    try (var st = connection.createStatement()) {
                        st.executeUpdate("""
                                ALTER TABLE topic
                                ADD CONSTRAINT post_content_check CHECK
                                (
                                    CASE
                                        WHEN DTYPE = 'Post' THEN
                                        CASE
                                           WHEN content IS NOT NULL
                                           THEN 1
                                           ELSE 0
                                           END
                                        ELSE 1
                                    END = 1
                                )
                                """);
                        st.executeUpdate("""
                                ALTER TABLE topic
                                ADD CONSTRAINT announcement_validUntil_check CHECK
                                (
                                    CASE
                                        WHEN DTYPE = 'Announcement' THEN
                                        CASE
                                           WHEN valid_until IS NOT NULL
                                           THEN 1
                                           ELSE 0
                                           END
                                        ELSE 1
                                    END = 1
                                )
                                """);

                    }
                }
        );
    }


    public Board creatBoard(Board board) {
        return boardRepository.save(board);
    }

    @Transactional
    // TODO @Transactional not required since board eagerly fetch topics, but better to have it
    public Post createPost(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        var post = new Post();
        post.setOwner("fdifrison");
        post.setTitle("Java Persistence");
        post.setContent("Learning from Vlad");
        post.setBoard(board);
        return postRepository.save(post);
    }

    public void createPost(Post post) throws SQLException {
        postRepository.save(post);
    }

    @Transactional
    // TODO @Transactional not required since board eagerly fetch topics, but better to have it
    public Announcement createAnnouncement(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        var announcement = new Announcement();
        announcement.setOwner("fdifrison");
        announcement.setTitle("Time to study!");
        announcement.setValidUntil(Instant.now().plus(Duration.ofDays(1)));
        announcement.setBoard(board);
        return announcementRepository.save(announcement);
    }


    @Transactional
    public void addStatistics(long topicId) {
        var topic = topicRepository.findById(topicId).orElseThrow();
        var stats = new TopicStatistics().topic(topic);
        stats.incrementViews();
        topicStatisticsRepository.save(stats);
    }

    /**
     * @implNote This is a polymorphic query since it return both the Topic children (post and announcement)
     */
    @Transactional
    public List<Topic> getBoardsTopics(long boardId) {
        var board = boardRepository.findById(boardId).orElseThrow();
        return topicRepository.findTopicsByBoard(board);
    }

    /**
     * @implNote Select all Topics row where dType=Post
     */
    public List<Topic> getAllPosts() {
        return topicRepository.findAllPosts();
    }

    /**
     * @implNote ordering by the entity.class make it possible for hibernate to use the dtype and distinguish between
     * Posts and Announcements
     */
    public List<Topic> getAllTopicsSortedByType() {
        return topicRepository.findTopicsSortedByType();
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

    @OneToMany(mappedBy = Topic_.BOARD) // bidirectional oneToMany
    private List<Topic> topics = new ArrayList<>();

    @Override
    public String toString() {
        return "Board{" +
                "name='" + name + '\'' +
                '}';
    }
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

    @Override
    public String toString() {
        return "Post{" +
                "id=" + super.getId() +
                ", title='" + super.getTitle() + '\'' +
                ", owner='" + super.getOwner() + '\'' +
                ", createdOn=" + super.getCreatedOn() +
                ", board=" + super.getBoard() +
                ", content='" + content + '\'' +
                '}';
    }
}

@Getter
@Setter
@Entity
class Announcement extends Topic {
    private Instant validUntil;

    @Override
    public String toString() {
        return "Announcement{" +
                "id=" + super.getId() +
                ", title='" + super.getTitle() + '\'' +
                ", owner='" + super.getOwner() + '\'' +
                ", createdOn=" + super.getCreatedOn() +
                ", board=" + super.getBoard() +
                ", validUntil=" + validUntil +
                '}';
    }
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
    // TODO statistics can be associated to both post and announcements
    private Topic topic;

    private long views;

    public void incrementViews() {
        this.views++;
    }

    @Override
    public String toString() {
        return "TopicStatistics{" +
                "topicId=" + topicId +
                ", topic=" + topic +
                ", views=" + views +
                '}';
    }
}
