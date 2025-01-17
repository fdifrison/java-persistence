<!-- TOC -->
* [`@ManyToOne`](#manytoone)
  * [bidirectional](#bidirectional)
* [Unidirectional `@OneToMany`](#unidirectional-onetomany-)
  * [join table](#join-table)
    * [List vs Set Collections](#list-vs-set-collections)
  * [`@JoinColumn`](#joincolumn)
* [`@OneToOne`](#onetoone)
  * [unidirectional](#unidirectional)
  * [bidirectional](#bidirectional-1)
* [`@ManyToMany`](#manytomany)
  * [Explicit mapping](#explicit-mapping)
<!-- TOC -->
# `@ManyToOne`

The `@ManyToOne` mapping is the most natural way to map a foreign key relationship between a parent and a child entity.
The annotation is placed on the child entity, usually with a LAZY behavior and optionally with a `@JoinColum` (mandatory
only if we use different naming in the entities' variables)

```java

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "post_id")
private Post post;
```

## bidirectional

If we need the relationship to be **bidirectional**, the parent entity must be hold a collection of the child entity
annotated with `@OneToMany`, specifying the mapping to the child variable holding the reference, the cascade type and
the
orphan removal strategy (usually, if the entities are strongly tied, the cascade type is set to ALL and the orphan
removal to true). Moreover, even if it is the child-side responsibility to sync the association, it is common to have
utility methods to synchronize both sides of the relationship if used in a `@Transactional` method.

```java

@OneToMany(
        mappedBy = Comment_.POST,
        cascade = CascadeType.ALL,
        orphanRemoval = true)
private List<Comment> comments = new ArrayList<>();

public void addComment(Comment comment) {
    comments.add(comment);
    comment.post(this);
}

public void removeComment(Comment comment) {
    comments.remove(comment);
    comment.post(null);
}
```

In this way, the removal of a comment is efficient since executes only one DELETE statement and remove the reference
from the Comment object so that it can be garbage collected.

# Unidirectional `@OneToMany` 
(DON'T USE IT IF YOU CAN)

Even if uncommon, we might opt to hold a unidirectional reference only on the parent-side of the relationship.
Its performance depends on the implementation and on the type of collection we implement, but always worse (less
performant) than a bidirectional one.

## join table

One solution would be to have a join table acting as collector of the parent-child relationship, however this means to
have an extra table with two foreign keys (and most probably two indexes) instead of only one on the child side.

To map the collection on the parent-side we can use either a `Set` or a `List`

### List vs Set Collections

While inserting elements in the two collections require the same effort, when coming to the remove of an element,
`Lists` are inefficients since they require the join table to remove all the rows associated with the id of the parent
entity to then re-add all the parent-child association rows except the one associated with the child entity we were
removing from the collection. To this, it must be added a re-balancing of the indexes in the join table two times, first
when the rows are removed, and then we are re-added.

If there is a meaning of ordering in the join column we could use the `List` collection together with the `@OrderColumn`
annotation to reduce the burden of using lists; in this way, if we want to remove the last element only two delete
statement are executed, one for the join table and one for the child table. However, if we are not removing the last
element, hibernate will execute an update statement for each row that will be shifted.

## `@JoinColumn`

An alternative, that requires the child-entity to hold a reference to the parent, is to annotate the parent-side
collection with the `@JoinColumn` annotation. However, this approach is also inefficient since for persisting elements
in the parent side collection hibernate will have to issue and insert statement and an update for each element
persisted. The update is required since the child entity is flushed before the parent-side collection, therefore,
hibernate has no clue about the foreign key value, hence an update is required to set the foreign key. If the option
`nullable=false` is specified in the `@JoinColumn` annotation, hibernate will flush the child entity with the foreign
key populated, but it will issue an update statement anyway.

Similarly, deleting an element from the parent-side collection has bad performance if the `nullable=false` is not set,
hibernate will first fire an update statement on the child entity to set the foreign key value to null, and only after
is will issue the delete statement to remove that same child entity. If `nullable=false` we save the first useless
update statement.

# `@OneToOne`

## unidirectional

The one-to-one unidirectional relationship can be mapped in two ways, with a `@JoinColumn` or with a `@MapsId`
annotation. In the first case we would need two indexes on the child table, one for its primary key and one for the
foreign key pointing to the parent entity.

With `@MapsId` we have several advantages, parent and child table shares the same primary key, therefore, even if the
relation is unidirectional (from the child side) we can access the parent or the child knowing one id; the indexes on
the child side are reduced to one.

## bidirectional

If it is required to access the child entity even from the parent side, a `@OneToOne` annotation is required on the
parent-side. However, there is the possibility of incurring in an N+1 performance bottleneck: in fact, since hibernate
needs to know if assign a null value or an object to the one-to-one mapping, a select query is performed for each post
entity retrieved in order to check, and eventually find, if there is a child entity connected.

Therefore, is a query like the following executed, n+1 queries are executed!

```java

@Query(value = """
        select * from post p
        where p.title like :title
        """, nativeQuery = true)
List<Post> findPostsWhereTitleIn(@Param("title") String title);
```

# `@ManyToMany`

In a many-to-many relationship, each side of the relation act as a parent-side, while the join table can be considered
as the child. The association can be either unidirectional or bidirectional, depending on the fact that we might need to
access the collection from both sides. However, in JPA terms, even in a bidirectional mapping, only one side will be
responsible to synchronize the mapping. Due to the parent-to-parent relation, the cascade type is confined to `PERSIST`
and `MERGE` operations, since none of the sides owns the other and, therefore, has no means to determine a cascade
delete for example. Furthermore, in the case of a bidirectional mapping, cascade delete would have catastrophic
consequence, since the deleting will be ping-ponged between the two entity, resulting in the complete deletion of all
records.

As for a unidirectional `@OneToMany` association there is a difference in the behavior triggered by the underling
collection type; as a matter of fact, while Lists and Sets behave equally for insertion, upon deletion Lists will first
remove all the rows associated with the id of the entity owner of the collection, to then reinsert all the rows but the
one that actually we wanted to remove. Sets instead execute a punctual delete on the row of the join table.

Usually, the join table is not modelled explicitly but embedded in one of the two parent-side with the `@JoinTable`
annotation

```java

@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
@JoinTable(
        name = "post_tag",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id"))
private Set<Tag> tags = new HashSet<>();
```

## Explicit mapping

The join table can also be explicitly mapped into an entity; in this case the relation between the two parent-side
becomes like a bidirectional `@OneToMany` mapping, and the use of cascade type ALL is possible since the two parent
entity doesn't speak directly one to another but only through the join table entity acting now as a child to both the
ends.

The join table can now contain more attributes than merely the two ids; the combined primary key, composed by the two
ids of the parents entities, id defined as an `@Embeddable`. The join table entity has therefore an `@EmbeddedId` and at
least two fields with a `@ManyToOne` annotation, pointing to the parents collections and with the `@MapsId` annotation,
delegating the foreign key reference to the embeddable type.

```java

@Entity
@Table(name = "post_tag")
class PostTag {

    public PostTag(Post post, Tag tag) {
        this.post = post;
        this.tag = tag;
        this.id = new PostTagId(post().id(), tag.id());
    }

    @EmbeddedId
    private PostTagId id;

    @ManyToOne
    @MapsId(PostTagId_.POST_ID)
    private Post post;

    @ManyToOne
    @MapsId(PostTagId_.TAG_ID)
    private Tag tag;

}

@Embeddable
record PostTagId(@Column(name = "post_id")
                 Long postId,
                 @Column(name = "tag_id")
                 Long tagId) {
}
```

From the parents side, we now have a collections of the new child entity, and we can use List without incurring in the
hibernate bag behavior seen in the unidirectional `@OnetoMany` mapping (i.e. we have a single delete statement instead
of a deleted all of n records where id = my_id and a n-1 insert back). The synchronization methods are again useful on
the parent side, even if their implementation is a bit more cumbersome since we need to keep in sync both ends of the
many-to-many association.

```java
public void addTag(Tag tag) {
    PostTag postTag = new PostTag(this, tag);
    tags.add(postTag);
    tag.posts().add(postTag);
}

public Post removeTag(Tag tag) {
    tags.stream().filter(t -> t.post().equals(this) && t.tag().equals(tag))
            .findFirst()
            .ifPresent(t -> {
                tags.remove(t);
                t.tag().posts().remove(t);
                t.post(null);
                t.tag(null);
            });
    return this;
}
```

