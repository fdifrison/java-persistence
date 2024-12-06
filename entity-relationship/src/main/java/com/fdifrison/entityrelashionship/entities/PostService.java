package com.fdifrison.entityrelashionship.entities;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

interface PostRepository extends JpaRepository<PostService.Post, Long> {}

@Service
public class PostService {
    private final PostRepository postRepository;

    PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public void findAll() {
        postRepository.findAll();
    }

    @Entity
    @Table(name = "post")
    public static class Post {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private long id;

        @Column(nullable = false)
        private String title;
    }
}
