package com.fdifrison.entityrelashionship.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "post")
public class Post2 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String title;

    public long getId() {
        return id;
    }

    public Post2 setId(long id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Post2 setTitle(String title) {
        this.title = title;
        return this;
    }
}
