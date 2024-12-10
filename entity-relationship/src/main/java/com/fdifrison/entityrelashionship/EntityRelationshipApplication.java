package com.fdifrison.entityrelashionship;

import com.fdifrison.entityrelashionship.entities.PostService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EntityRelationshipApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityRelationshipApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(PostService postService) {
        return args -> {
            //            var postId = postService.savePost().getId();
            //            var postCommentId = postService.addComment(postId).getId();
            //            postService.unlinkComment(postCommentId);
        };
    }
}
