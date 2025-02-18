# Java Persistence

Best practices in java/spring-jpa persistence
following Vlad Mihalcea courses https://vladmihalcea.com/courses/ and blog posts https://vladmihalcea.com/blog/

## Organization

- _**doc**_ folder contains theory summaries on the topics covered in the courses and blog posts
- _**entity-relationship**_ contains tests on the best and worst practices to code entities mapping.
- _**inheritance**_ how and when to use polymorphism in the persistence layer (reads single-table-inheritance)

N.B.
Separate dbs and changelogs are used to have a representative environment in each situation. N.B. be sure to activate
the correct spring profile (it has to match the one requested in the context of each class).



# Spring Data, JPA, and Hibernate Annotations Reference

## Entity Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Entity` | `jakarta.persistence` | Marks a class as an entity (i.e., a persistent domain object). Required for JPA entities. |
| `@Table` | `jakarta.persistence` | Specifies the table name for the entity. Optional if entity name matches table name. |
| `@Id` | `jakarta.persistence` | Marks a field as the primary key. |
| `@GeneratedValue` | `jakarta.persistence` | Specifies strategy for generating primary key values. Common strategies: `AUTO`, `IDENTITY`, `SEQUENCE`, `TABLE`. |
| `@Column` | `jakarta.persistence` | Specifies column mapping details (name, nullable, unique, length, etc.). |
| `@Transient` | `jakarta.persistence` | Marks a field as non-persistent (i.e., not stored in database). |
| `@Temporal` | `jakarta.persistence` | Specifies temporal precision for date/time fields (`DATE`, `TIME`, `TIMESTAMP`). |
| `@Enumerated` | `jakarta.persistence` | Specifies how to persist enum values (`STRING` or `ORDINAL`). |
| `@Lob` | `jakarta.persistence` | Marks a field as Large Object (for storing large data like text or binary content). |

## Relationship Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@OneToOne` | `jakarta.persistence` | Defines a one-to-one relationship between entities. |
| `@OneToMany` | `jakarta.persistence` | Defines a one-to-many relationship between entities. |
| `@ManyToOne` | `jakarta.persistence` | Defines a many-to-one relationship between entities. |
| `@ManyToMany` | `jakarta.persistence` | Defines a many-to-many relationship between entities. |
| `@JoinColumn` | `jakarta.persistence` | Specifies the foreign key column in relationships. |
| `@JoinTable` | `jakarta.persistence` | Specifies the join table for `@ManyToMany` relationships. |
| `@ForeignKey` | `org.hibernate.annotations` | Defines the constraint for a foreign key. |
| `@MapsId` | `jakarta.persistence` | Maps a relationship to use the identifier of the related entity. |

## Inheritance Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Inheritance` | `jakarta.persistence` | Specifies inheritance strategy for entity class hierarchies: `SINGLE_TABLE`, `JOINED`, or `TABLE_PER_CLASS`. |
| `@DiscriminatorColumn` | `jakarta.persistence` | Specifies the discriminator column for `SINGLE_TABLE` inheritance strategy. |
| `@DiscriminatorValue` | `jakarta.persistence` | Specifies the discriminator value for a specific entity in the hierarchy. |
| `@MappedSuperclass` | `jakarta.persistence` | Marks a class as a mapped superclass (not an entity itself, but provides persistent properties to subclasses). |

## Query Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Query` | `org.springframework.data.jpa.repository` | Defines a custom JPQL or native SQL query for repository methods. |
| `@Procedure` | `org.springframework.data.jpa.repository` | Maps a repository method to a stored procedure. |
| `@Modifying` | `org.springframework.data.jpa.repository` | Indicates that a query method should modify data (UPDATE or DELETE). |
| `@NamedQuery` | `jakarta.persistence` | Defines a named JPQL query at the entity level. |
| `@NamedNativeQuery` | `jakarta.persistence` | Defines a named native SQL query at the entity level. |
| `@QueryHints` | `org.springframework.data.jpa.repository` | Applies JPA query hints to the query. |
| `@Lock` | `org.springframework.data.jpa.repository` | Specifies the lock mode for the query. |

## Spring Data Repository Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Repository` | `org.springframework.stereotype` | Marks a class as a repository, eligible for Spring exception translation. |
| `@RepositoryDefinition` | `org.springframework.data.repository` | Creates a custom repository interface without extending standard interfaces. |
| `@NoRepositoryBean` | `org.springframework.data.repository` | Indicates that an interface should not be instantiated as a repository. |
| `@Param` | `org.springframework.data.repository` | Binds method parameters to query parameters in custom queries. |
| `@EnableJpaRepositories` | `org.springframework.data.jpa.repository.config` | Enables JPA repositories in Spring Boot application. |

## Transaction Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Transactional` | `org.springframework.transaction.annotation` | Declares transaction boundaries and attributes for methods or classes. |
| `@Rollback` | `org.springframework.test.annotation` | Specifies whether a test-managed transaction should be rolled back. |
| `@Commit` | `org.springframework.test.annotation` | Specifies that a test-managed transaction should be committed. |

## Auditing Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@CreatedDate` | `org.springframework.data.annotation` | Marks a field to be set with creation timestamp. |
| `@LastModifiedDate` | `org.springframework.data.annotation` | Marks a field to be set with last modification timestamp. |
| `@CreatedBy` | `org.springframework.data.annotation` | Marks a field to be set with the creator's ID. |
| `@LastModifiedBy` | `org.springframework.data.annotation` | Marks a field to be set with the last modifier's ID. |
| `@EnableJpaAuditing` | `org.springframework.data.jpa.repository.config` | Enables JPA auditing in Spring Boot application. |

## Hibernate-Specific Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@Formula` | `org.hibernate.annotations` | Defines a SQL expression to compute a column value. |
| `@Where` | `org.hibernate.annotations` | Adds an SQL WHERE clause to an entity or collection mapping. |
| `@Filter` | `org.hibernate.annotations` | Enables dynamic filtering of query results. |
| `@FilterDef` | `org.hibernate.annotations` | Defines a named filter with parameters. |
| `@Cache` | `org.hibernate.annotations` | Specifies caching strategy for an entity or collection. |
| `@DynamicUpdate` | `org.hibernate.annotations` | Instructs Hibernate to include only changed columns in the UPDATE statement. |
| `@DynamicInsert` | `org.hibernate.annotations` | Instructs Hibernate to include only non-null columns in the INSERT statement. |
| `@OptimisticLocking` | `org.hibernate.annotations` | Specifies the optimistic locking strategy. |
| `@NaturalId` | `org.hibernate.annotations` | Marks properties that form a natural ID. |
| `@Immutable` | `org.hibernate.annotations` | Marks an entity or collection as immutable (read-only). |

## Validation Annotations

| Annotation | Package | Description |
|------------|---------|-------------|
| `@NotNull` | `jakarta.validation.constraints` | Ensures a field is not null. |
| `@Size` | `jakarta.validation.constraints` | Validates that the size of a string, collection, or array is within boundaries. |
| `@Min` | `jakarta.validation.constraints` | Validates that a number is greater than or equal to the value. |
| `@Max` | `jakarta.validation.constraints` | Validates that a number is less than or equal to the value. |
| `@Pattern` | `jakarta.validation.constraints` | Validates that a string matches a regex pattern. |
| `@Email` | `jakarta.validation.constraints` | Validates that a string is a valid email address. |
| `@Valid` | `jakarta.validation` | Cascades validation to associated objects. |