# @ManyToOne

The @ManyToOne mapping is the most natural way to map a foreign key relationship between a parent and a child entity.
The annotation is placed on the child entity, usually with a LAZY behavior and optionally with a @JoinColum (mandatory
only if we use different naming in the entities' variables)

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "post_id")
private Post post;
```

## bidirectional
If we need the relationship to be **bidirectional**, the parent entity must be hold a collection of the child entity annotated with @OneToMany
