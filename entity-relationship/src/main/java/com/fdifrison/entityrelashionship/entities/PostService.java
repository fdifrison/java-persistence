package com.fdifrison.entityrelashionship.entities;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Repository
interface PostRepository extends JpaRepository<PostService.Post, Long> {}

@Repository
interface PostCommentRepository extends JpaRepository<PostService.PostComment, Long> {}

@Service
public class PostService {
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    PostService(PostRepository postRepository, PostCommentRepository postCommentRepository) {
        this.postRepository = postRepository;
        this.postCommentRepository = postCommentRepository;
    }

    public Post savePost() {
        return postRepository.save(new Post("a new post"));
    }

    public PostComment addComment(long id) {
        var post = postRepository.getReferenceById(id);
        var postComment = new PostComment("a comment");
        postComment.setPost(post);
        return postCommentRepository.save(postComment);
    }

    @Transactional
    public void unlinkComment(long id) {
        var comment = postCommentRepository.findById(id).orElseThrow();
        comment.setPost(null);
    }

    public void deleteComment(long id) {
        postCommentRepository.deleteById(id);
    }

    @Entity
    @Table(name = "post")
    public static class Post {

        public Post() {}

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
    public static class PostComment {

        public PostComment(String comment) {
            this.comment = comment;
        }

        public PostComment() {}

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

        public PostComment setId(long id) {
            this.id = id;
            return this;
        }

        public String getComment() {
            return comment;
        }

        public PostComment setComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Post getPost() {
            return post;
        }

        public PostComment setPost(Post post) {
            this.post = post;
            return this;
        }
    }
}
