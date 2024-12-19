package com.fdifrison.entityrelashionship.entities;

import jakarta.persistence.*;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ManyToOneAndOneToMany {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ManyToOneAndOneToMany.class)
                .bannerMode(Banner.Mode.CONSOLE)
                .run(args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {
            var post = postService.savePost();
            var comment = postService.addComment(post.getId());
            postService.linkCommentToPost(comment.getId(), post);
            postService.findCommentAndSetPostReferenceToNull(comment.getId());
        };
    }
}

@Repository
interface PostRepository extends JpaRepository<PostService.Post, Long> {
}

@Repository
interface PostCommentRepository extends JpaRepository<PostService.Comment, Long> {
}

@Service
class PostService {


    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    PostService(PostRepository postRepository, PostCommentRepository postCommentRepository) {
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post("a new post"));
    }

    public Comment addComment(long id) {
        var postComment = new Comment("a comment");
        return postCommentRepository.save(postComment);
    }

    /**
     * @implNote
     * since @Transactional is active and since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to the already persisted Post object hibernate will execute the update statement
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void linkCommentToPost(long postId, Post post) {
        var comment = postCommentRepository.findById(postId).orElseThrow();
        comment.setPost(post);
    }

    /**
     * @implNote since the comment entity is not part of the transaction setting the reference to the post won't fire
     * any update statement
     */
    @Transactional
    public void linkCommentToPostWontUpdate(Comment comment, Post post) {
        comment.setPost(post);
    }



    /**
     * @implNote
     * since @Transactional is active and since comment as a foreign key with a @ManyToOne annotation on
     * post_id, by setting the reference to null hibernate will execute the update statement
     * @apiNote a SELECT and an UPDATE statement are executed
     */
    @Transactional
    public void findCommentAndSetPostReferenceToNull(long id) {
        var comment = postCommentRepository.findById(id).orElseThrow();
        comment.setPost(null);
    }

    public void deleteComment(long id) {
        postCommentRepository.deleteById(id);
    }

    @Entity
    @Table(name = "post")
    public static class Post {

        public Post() {
        }

        public Post(String title) {
            this.title = title;
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        @Column(nullable = false)
        private String title;

        public long getId() {
            return id;
        }

        public Post setId(long id) {
            this.id = id;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Post setTitle(String title) {
            this.title = title;
            return this;
        }
    }

    @Entity
    @Table(name = "post_comment")
    public static class Comment {

        public Comment(String comment) {
            this.comment = comment;
        }

        public Comment() {
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        private String comment;

        @ManyToOne(fetch = FetchType.LAZY)
        //  TODO Could be omitted since by default hibernate join the name of the identity attribute with identifier
        //   using an underscore
        @JoinColumn(name = "post_id")
        private Post post;

        public long getId() {
            return id;
        }

        public Comment setId(long id) {
            this.id = id;
            return this;
        }

        public String getComment() {
            return comment;
        }

        public Comment setComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Post getPost() {
            return post;
        }

        public Comment setPost(Post post) {
            this.post = post;
            return this;
        }
    }
}
