package com.fdifrison.joined;

import com.fdifrison.configurations.Profiles;
import com.fdifrison.utils.Printer;
import jakarta.persistence.*;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Joined {

    public static void main(String[] args) {
        new SpringApplicationBuilder(Joined.class)
                .profiles(Profiles.Active.joined.name())
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

            Printer.focus("Adding statistics");
            service.addStatistics(post.getId());
            service.addStatistics(announcement.getId());

            Printer.focus("Performing a polymorphic query to retrieve all topics from a board");
            var boardsTopics = service.getBoardsTopics(board.id());
            Printer.entityList(boardsTopics);

        };
    }
}

interface BoardRepository extends JpaRepository<Board, Long> {


    @Query(value = """
            select b from Board b join fetch b.topics where b.id = :id
            """)
    Optional<Board> findBoardByIdFull(@Param("id") Long id);

}

interface PostRepository extends JpaRepository<Topic<Post>, Long> {
}

interface AnnouncementRepository extends JpaRepository<Topic<Announcement>, Long> {
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
    List<Topic<Post>> findAllPosts();

}

interface TopicStatisticsRepository extends JpaRepository<TopicStatistics, Long> {
}

@Service
class TestService {

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final AnnouncementRepository announcementRepository;
    private final TopicRepository topicRepository;
    private final TopicStatisticsRepository topicStatisticsRepository;


    TestService(
            BoardRepository boardRepository,
            PostRepository postRepository,
            AnnouncementRepository announcementRepository,
            TopicRepository topicRepository,
            TopicStatisticsRepository topicStatisticsRepository) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.announcementRepository = announcementRepository;
        this.topicRepository = topicRepository;
        this.topicStatisticsRepository = topicStatisticsRepository;
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
     * @apiNote 1 INSERT for the parent table topic + 1 INSERT for the child table post
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
     * @apiNote 1 INSERT for the parent table topic + 1 INSERT for the child table announcement
     */
    private Announcement createAnnouncement(Announcement announcement) {
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
     * @apiNote Hibernate needs to have the fully resolved entity, hence it needs to perform a left join with both the
     * child table
     */
    @Transactional
    public List<Topic> getBoardsTopics(long boardId) {
        var board = boardRepository.findBoardByIdFull(boardId).orElseThrow();
        return board.topics();
    }

    /**
     * @implNote Select all Topics row where dType=Post
     */
    public List<Topic<Post>> getAllPosts() {
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
@Entity
@Table
@Inheritance(strategy = InheritanceType.JOINED) // default inheritance type
class Topic<T extends Topic<T>> {

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
@Table
// TODO no inheritance strategy is required on the child entities since they inherit from the parent class
class Post extends Topic<Post> {
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

    public Post setContent(String content) {
        this.content = content;
        return this;
    }
}

@Getter
@Entity
@Table
// TODO no inheritance strategy is required on the child entities since they inherit from the parent class
class Announcement extends Topic<Announcement> {
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

    public Announcement setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
        return this;
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
    // TODO the primary key is shared with the parent table topic but its not foreign key to the child tables, even
    //  if the value is the same
    @JoinColumn(name = TopicStatistics_.TOPIC_ID)
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
