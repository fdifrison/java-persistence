package com.fdifrison.entityrelashionship.entities;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PostTest extends AbstractContainerBaseTest {

    @Autowired
    private PostRepository postRepository;

    @Test
    void testSave() {
        postRepository.save(new PostService.Post( "Test Description"));
    }
}
