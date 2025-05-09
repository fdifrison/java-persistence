<h1>Java Persistence</h1>

Best practices in java/spring-jpa persistence
following Vlad Mihalcea courses https://vladmihalcea.com/courses/ and blog posts https://vladmihalcea.com/blog/

<h2>Organization</h2>

- _**entity-relationship**_ contains tests on the best and worst practices to code entities mapping.
- _**inheritance**_ how and when to use polymorphism in the persistence layer (reads single-table-inheritance)

N.B.
Separate dbs and changelogs are used to have a representative environment in each situation. N.B. be sure to activate
the correct spring profile (it has to match the one requested in the context of each class).

---

# Theory

<!-- TOC -->
* [Theory](#theory)
* [ACID](#acid)
  * [Atomicity](#atomicity)
  * [Consistency](#consistency)
    * [CAP theorem](#cap-theorem)
  * [Durability](#durability)
  * [Isolation (added il SQL 92)](#isolation-added-il-sql-92)
    * [Concurrency control](#concurrency-control)
    * [MVCC](#mvcc)
      * [Amdahl's law](#amdahls-law)
  * [Phenomena](#phenomena)
* [Locks](#locks)
  * [Pessimistic locks](#pessimistic-locks)
    * [Hibernate-specific LockOptions](#hibernate-specific-lockoptions)
    * [SKIP_LOCKED](#skip_locked)
    * [Advisory lock](#advisory-lock)
  * [Optimistic Locks](#optimistic-locks)
    * [Preventing lost update](#preventing-lost-update)
    * [Scaling optimistic locking](#scaling-optimistic-locking)
    * [Explicit Optimistic locking modes](#explicit-optimistic-locking-modes)
* [Database Caching](#database-caching)
  * [Caching layers](#caching-layers)
    * [Database and OS cache](#database-and-os-cache)
      * [Postgresql caching](#postgresql-caching)
  * [Application-level caching](#application-level-caching)
  * [Cache synchronization](#cache-synchronization)
    * [Cache-aside](#cache-aside)
      * [CDC Change Data Capture](#cdc-change-data-capture)
      * [Cache ripple effect](#cache-ripple-effect)
  * [Hibernate Second-Level cache](#hibernate-second-level-cache)
* [Connections](#connections)
* [Persistence Context in JPA and Hibernate](#persistence-context-in-jpa-and-hibernate)
  * [Caching](#caching)
  * [Entity state transitions](#entity-state-transitions)
    * [JPA EntityManager](#jpa-entitymanager)
    * [Hibernate Session](#hibernate-session)
      * [JPA merge vs Hibernate update](#jpa-merge-vs-hibernate-update)
  * [Dirty checking](#dirty-checking)
    * [Bytecode enhancement](#bytecode-enhancement)
  * [Hydration -> read-by-name (Hibernate < 6.0)](#hydration---read-by-name-hibernate--60)
  * [Flushing](#flushing)
    * [AUTO flushing mode](#auto-flushing-mode)
    * [Flushing in batch processing](#flushing-in-batch-processing)
  * [Events and event listener](#events-and-event-listener)
* [SQL Statements: lifecycle, execution plan and caching](#sql-statements-lifecycle-execution-plan-and-caching)
  * [Execution plan cache](#execution-plan-cache)
  * [Prepared statement](#prepared-statement)
  * [Client-Side vs. Server-Side statement caching:](#client-side-vs-server-side-statement-caching)
* [Fetching](#fetching)
  * [Fetching associations](#fetching-associations)
    * [N+1 query problem](#n1-query-problem)
    * [Fetching multiple collections](#fetching-multiple-collections)
  * [Open Session in View](#open-session-in-view)
* [Projections](#projections)
  * [JPA projections](#jpa-projections)
    * [Tuple](#tuple)
    * [DTO](#dto)
      * [mapping native SQL queries](#mapping-native-sql-queries)
  * [Hibernate projections](#hibernate-projections)
  * [Bets approach for projecting parent-child relationship](#bets-approach-for-projecting-parent-child-relationship)
* [Batching in Hibernate](#batching-in-hibernate)
  * [Bulking operations](#bulking-operations)
  * [Batching in cascade](#batching-in-cascade)
    * [DELETE cascade](#delete-cascade)
    * [Batching on versioned entity](#batching-on-versioned-entity)
  * [Default UPDATE behavior](#default-update-behavior)
* [Primary Keys and JPA identifiers](#primary-keys-and-jpa-identifiers)
  * [JPA identifiers](#jpa-identifiers)
* [Entity Relationship](#entity-relationship)
  * [`@ManyToOne`](#manytoone)
    * [bidirectional](#bidirectional)
  * [Unidirectional `@OneToMany`](#unidirectional-onetomany)
    * [join table](#join-table)
      * [List vs Set Collections](#list-vs-set-collections)
    * [`@JoinColumn`](#joincolumn)
  * [`@OneToOne`](#onetoone)
    * [unidirectional](#unidirectional)
    * [bidirectional](#bidirectional-1)
  * [`@ManyToMany`](#manytomany)
    * [Explicit mapping](#explicit-mapping)
* [JPA inheritance](#jpa-inheritance)
  * [Single table inheritance](#single-table-inheritance)
    * [`@DiscriminatorColumn` and `@DiscriminatorValue`](#discriminatorcolumn-and-discriminatorvalue)
  * [Joined inheritance](#joined-inheritance)
  * [Table per class](#table-per-class)
  * [`@MappedSuperclass`](#mappedsuperclass)
* [EnumType](#enumtype)
<!-- TOC -->

---

# ACID

To better understand the ACID acronym, we should first dive a bit in how a relational database works.

![](./images/acid/db.png)

At the very bottom we have the physical disk where the data are stored in tables/indexes; in fact, the unit of a
database memory is not bytes, but pages (usually 8 or 16 kilobytes). Operating on the physical disk is very slow
compared to RAM; for these reasons all the databases (also the NoSql) have an intermediate layer called
`In-memory buffers` which synchronize a subset of the data on the disk; these are then modified and only at flush time
the new state is copied on disk (in the same way Hibernate. when loading an entity, it stores it in the first-level
cache).

Due to I/O bandwidth, it is not possible to synchronize each transaction back to the disk because it would mean to move
a potential huge amount of data very often (imagine an application that handles hundreds of transactions per second).
For example, PostgreSQL, which by default allocates 25% of the RAM to the In-memory buffer, flushes the entire cache
only once every 5 minutes. So what happens if we have a crash? Would we lose all the non-synchronized data in the cache?

Of course this is not possible; therefore, we have another layer of insurance, represented by the `Redo log`. Every time
we are commiting a writing, the database appends to the Redo log our scheduled changes. In this way, if the changes have
not been synchronized in memory, or not flushed on the disk, when the database restarts will check the redo log and try
to replay the operations left behind.

Similarly, the `Undo log` is required to be able to roll back to the previous consistent state.

An exception is PostgreSQL that doesn't use an append-only undo logs; instead it uses a `multi-version approach` which
consists in keeping multiple versions of the same record in memory. This behaviour allows faster rollbacks since no diff
has to be computed and no log search is performed, we simply switch from one version of the object to another. The
downside is that the previous version memory space is limited and has to be reclaimed regularly with the `VACUUM`
operation, otherwise we may incur in a very disruptive behaviour. Postgres associate with a transaction a 32-bit XID (
with the constraint that newer transactions must have a greater XID); in a high performance application, we very short
and frequent transactions, we might fill the four-billion transaction limit (given by the 32-bit size of the ID). If the
VACUUM process is disabled, the XID counter will start from zero, making newer transactions look like older ones, hence
destroying the database).

## Atomicity

Atomicity is the property of grouping a set of operations and execute them in a `unit of work`, meaning that or all the
operations succeed or, even if one fails, the whole unit of work fails (rollback). The database has to pass from a
consistency state to another at the end of the atomic operation. Rolling back is the action required to return to the
previous consistent state in the case of a failure in one of the operations in the transaction.

## Consistency

Consistency is the property ensuring that a transaction state change leaves the database in a proper state, without
violating the constraint described by the schemas (column type, nullability, pk and fk constraints etc.).
Again, if only one validation fails, all the transaction is rolled back and the database state is restored to prior the
transaction

### CAP theorem

The CAP theorem states that when a distributed system encounters a network partition, it needs to choose between
Consistency and Availability, can't have both. However, in this context, Consistency has a different meaning that in
ACID since it refers to an isolation guarantee called `linearizability`, i.e., the ability to always read the latest
state of a variable (something that in a distributed system, where we have follower nodes from which we can also read
and a replication lag, it is not guaranteed)

## Durability

Durability ensures that all committed transaction changes become permanent, something that it's ensured by the
`redo log`.

In Postgres there is something equivalent called `WAL` Write-Ahead Log which can be flushed asynchronously.
While the log entries are buffered in memory and flushed every transaction commits, the cashed pages and indexes don't
since their state can be restored from the WAL, thus optimising I/O utilization.

## Isolation (added il SQL 92)

Databases are not meant to be accessed by only one user at a time.
They instead need to sustain multiple concurrent connections; there is a need to ensure `Serializability`, meaning that
even in a concurrent environment we need an outcome equivalent to a serial execution. Therefore, rules are required to
orchestrate the concurrent reads and writes so that conflicts don't occur, compromising data integrity. Note that
serializability itself doesn't concern about time, instead we can think it as the property that ensures that the **reads
and the writes of a user `A` are not interleaved by reads or writes of user `B`, but the operation of `A` and `B` can
still be interchanged in time.**
Linearizability instead is about time; it concerns the ability to read the latest state of a variable.
The conjunction of serializability and linearizability is the golden standard of isolation level:
`Strict Serializability` which, however, almost always comes at an unbearable cost for a real production environment.

As a fact, we always have to come down to a compromise, which ensures a satisfactory level of isolation while
still enabling a sufficient concurrency (we are most of the time in a `read commited` isolation level) or, in
alternative, use a persistence provider like `VoltDB` which works only in-memory and single threaded, thus guarantying
Serializability.

![](./images/acid/isolation.png)

### Concurrency control

The strategies applied to RDBMS to avoid data conflicts fall under the name of `concurrency control`. We can choose to
avoid the conflicts completely (e.g. two-phase locking) controlling access to shared resources, or to somehow detecting
the conflicts (e.g. MVCC multi-version concurrency control), with a gain in performance (better concurrency) but
relaxing the serializability constraint and accepting possible data anomalies.

A certain number of locks will be required whichever path we choose, in two-phase locking we simply have more. Also,
locking can happen at different database hierarchy levels, from rows to pages; depending on the use case the database
could choose to substitute multiple low levels lock on rows to a single upper level lock on the page (since locks are
takes resources). Two-phase locking requires a lot of waiting time since, to achieve strict serializability, read lock
blocks write operations and write lock block read operations.
Even if each database system has its own lock hierarchy, the most common are:

* `shared lock` (read lock) which allows for concurrent reads but prevent writing, hence if I acquire a read lock, also
  others can have it, but no one can write on the record
* `exclusive lock` (write lock) preventing both read and write operations, hence nobody can read or write the record
  until my modification are not committed

No matter the strategy we choose, the database has to acquire an exclusive lock when a user wants to commit a change to
a record; therefore, if multiple users are trying to perform an operation that will eventually impact the same record a
`deadlock` can happen, meaning that both the users hold a lock on interconnected data (think of a parent post entity and
a child comment entity). If this happens, the database will need to kill one of the two processes, triggering a rollback
and the release of the locks, so that at least one transaction can be finalised. Usually, the discriminant is always the
cost in terms of resources for the database, the operation that requires less effort to resolve the deadlock will be
performed (e.g. the transaction that holds fewer locks is more likely to be killed). Some databases instead use time as
discriminant: the transaction that started first is the one that holds the right to survive.

In Java, `Synchronized` is a `exclusive lock`, while `shared lock` can be implemented with `ReadWriteLock` from the
concurrency API.

### MVCC

Most database systems nowadays use by default a `Multi-Version Concurrency Control` to overcome the transaction response
time and the scalability issue inherent in the two-phase locking mechanism. The premises of MVCC are that readers and
writers do not block each other; the only source of contention is concurrent writers which will undermine atomicity.
Essentially, to prevent blocking, a previous version of a record can be rebuild so that an uncommited change can be
hidden from concurrent readers. The name multi-version effectively comes from the fact that at a certain point in time,
we can have multiple versions of the same record. For example, postgresql stores two additional columns in our entities,
`xmin` and `xmax`, which are used to control the visibility of various row versions; an update statement first deletes a
record and then recreates it with the updated value. However, until committed, both versions of the record exist. Other
database vendors have only one version of the record at the time but store the difference between caused by the update
in the undo log.

#### Amdahl's law

![](./images/acid/amdahl.png)

The more locks our application needs to acquire, the less parallelization we can achieve, and this is shown by the
`Amdahl's law` with the correction of `Neil Gunter` which states that the throughput that we can achieve `C(N)` has a
saturation point in which, increasing the number of threads or connections `N` won't increase performances; on the
contrary, performances will start deteriorating due to the cost of synchronisation (coherency cost).
This is one of the reasons why two-phase locking has been abandoned, since moder application now requires a number of
transactions per seconds that are orders of magnitude greater tha when the approach was developed in 1981.
Only `SQL server` nowadays still uses two-phase locking has the default mechanism.

## Phenomena

The new paradigm of abandoning strict serializability for a lower isolation level, opens the door to `phenomena`, hence
the possibility of data inconsistency. The lower is the isolation level, the more phenomena are allowed; this means that
**we are shifting the responsibility of data integrity from the database to the application level**.

The phenomena are:

* `dirty read`: a user reads a not yet committed write from another user; if the write operation abort and rollback, a
  database record does not reference what the first user is reading, since it was only a temporary value. This can
  sometimes be a feature, for example, to read the intermediate state of a batch operation involving multiple rows, or
  to know the advancement status of the batch process. If a read lock is used, dirty read is not possible but also the
  ability to read the intermediate state. (the same operation can be achieved by `read uncommitted` isolation level but
  not all the database systems allow it)
* `non-repeatable read`: two consecutive reads by the same user return a different result because an update operation of
  a second user interleaved; this can be a problem because the first user might have done some logic based on the first
  read that is no more applicable with the result of the second read
* `phantom read`: similar to non-repeatable reads but extended to a whole result set and not a single record (e.g. a
  find all on the comments of a post). To avoid this, we need a `predicate lock`.
* `dirty write` (theoretical): a record modified by two separate transactions with the first transaction yet not
  committed (no exclusive lock is taken by the first transaction). Since it breaks atomicity (the database doesn't know
  to which state rollback), it is prevented by all database vendor
* `read skew`: means reading from different transactions states; imagine a user is reading a post in its transaction and
  in the same time another user modifies the same post, and its child entity post_detail and commits. Now, if the first
  user, in the same transaction tries to access the post_detail will see the changes from the second user while still
  seeing the post, fetched previously, as unmodified.
* `write skew`: similar to read skew, the writes from two users that reads the same record that holds a child entity and
  then try to modify it in the same transaction gets mixed; this cannot be prevented in t MVCC because we can have a
  lock only on a single record and not on its entire graph.
* `lost update`: a user reads a record, and before modifying it another user reads the same record and performs an
  update; the first users update won't be aware of the changes performed by the second user leading to an unexpected
  outcome since the first user starting point is different from the current state of the record in the database, hence
  an update has been lost from the first user

![](./images/acid/levels.png)

N.B. in PostgreSQL `Serializable` is not achieved by tho-phase locking but by a new implementation called
`serializable snapshot isolation` which check the schedule and determines if there are cycles.
Also, `repetable reads` views the database as of the beginning of the user transaction while `read committed` views the
database as of the beginning of the query

N.B. `repetable read` is equivalent to `snapshot isolation`

---

# Locks

Lock can be fundamentally divided in two types: `Physical` or `Pessimistic` locks and `Logical` or `Optimistic` locks.

## Pessimistic locks

Pessimistic lock divides in two subcategories:

* Implicit -> Isolation level (see [](#isolation-added-il-sql-92))
* Explicit -> SQL statements like `FOR UPDATE` or `FOR SHARE` in postgres

Focusing on postgres, we have the `LockMode.PESSIMISTIC_READ` which is equivalent to the `FOR SHARE` sql statement
(other databases like Oracle don't have it, it falls back to an exclusive lock) which allows other to read the same
record but not to acquire an exclusive lock, like a `FOR UPDATE` statement, which will modify the record.

The `LockMode.PESSIMISTIC_WRITE` is equivalent to the `FOR UPDATE` sql statement and correspond to an exclusive lock.
Basically we emulate 2PL on top of MVCC, nobody can update or delete the record that we've previously locked. In
postgres, a user can still insert a record associated with the predicate that has been locked (e.g. the first user has
locked for update a post and all its comments, a second user can still insert a new comment). However, this is a
peculiarity of postgres; MySql, for example, will block the insert until the lock is released. This is called a
`Predicate lock` and postgres don't support it natively; to have the same behaviour we need to explicitly say that we
want a lock on the whole table with:

```sql
LOCK TABLE post
    IN SHARE ROW EXCLUSIVE MODE
    NOWAIT 
```

### Hibernate-specific LockOptions

Hibernate has its lock option enumeration that allows for finer grain the lock mode; for example, we can specify how
long we allow the transaction to wait for lock acquisition (equivalent to add a sql `FOR UPDATE WAIT`). Similarly,
`NO_WAIT` (equivalent to `FOR UPDATE NOWAIT` in SQL) has the same purpose, meaning that we want to avoid the transaction
to stall while acquiring a lock; with NO WAIT we are telling the transaction to check if a lock is present on the record
nad in that case to immediately abort.

### SKIP_LOCKED

`LockOptions.SKIP_LOCKED` is the only way we can execute queue-based processing tasks on a relational database. Imagine
to have a queue of jobs that needs to be processed by multiple workers, and we need to ensure that each job is processed
only once. To avoid that the same job is taken, we could think to use the `FOR UPDATE` lock, but this will create a
bottleneck where all the workers fight for the same lock. By using `FOR UPDATE SKIP LOCKED` instead we are allowing for
parallel processing of the jobs by multiple workers since each will try to find an unlocked job without waiting.

### Advisory lock

Advisory locks are an exclusive of postgres; they can be both session-based or transaction-based and are means to
perform application-level concurrency control. Session-based advisory locks are not reentrant, meaning that once
acquired, they need to be explicitly released; Transaction-based advisory locks instead, like any other row-level locks,
are released automatically when the transaction is committed or rolled back.
The advisory locks can be `shared` or `esclusive`  with the same behaviour of a FOR SHARED and FOR UPDATE lock.
Advisory locks are a low level way to reduce the risks of race conditions, in particular when more than one server nodes
act on a single persistence unit; since they are at database level, they can span across multiple threads and JVM nodes.

Imaging to have several nodes trying to read the same file which has a certain size and therefore takes some time to be
buffered; we want each node to be able to read a consistent state of the file, i.e. we want it to have a shared lock so
that other nodes are able to read the file as well, but none is able to modify it until every reader as concluded the
buffering and released the shared lock.
If one node wants to write to the file, it needs to acquire an exclusive lock since we don't want other nodes to be able
to read an inconsistent state.

## Optimistic Locks

Optimistic locks divides in two subcategories:

* Implicit -> `@Version`
* Explicit -> `LockModeType.OPTIMISTIC_FORCE_INCREMENT`

Other than rely solely on database-specific locking strategy, we can move the concurrency control to the application
level, implementing our own algorithm or relying on the support of data access framework such as Hibernate.
One way to implement optimistic locking is by the use of one extra column that stores the version of the record we are
reading. This is called a `clock` that can be either physical (i.e. a column storing a timestamp) or logical with
`@Version`, basically an incremental number. Computers don't have an atomic clock, therefore using a physical clock is
generally not a good choice; there can be oscillation (in terms of milliseconds) due to the adjustments that the network
time protocol tries to perform on the System clock. This means that `System.currentTimeMillis` is not strictly
monotonic. Moreover, there are leap seconds and time zones that are other challenges that might become a problem when
trying to perform a concurrency control using a timestamp.

Hence, the better alternative to apply implicit optimistic locking is to use a `@Version`, an incremental number, which
can easily be a `short`, not even an `int` (if we don't think that our record might get changed more than 65k times
between a read and a write). N.B. the difference between an int and a short is 2 bytes for each record, if we have a
lot of records, it will matter!

```java

@Version
private short version;
```

The needs of application-level transaction arise from the fact that a logical transaction may be composed by multiple
web requests that include the time the user thinking time; we call these `long conversation`.
Span a database transaction over a long conversation is not practical since it would mean that a lock is held for a very
long time, hurting scalability.

### Preventing lost update

Imagine being on a shopping website, where we select a product, put it in our basket, and then we spend some time
thinking if actually we want to buy it. Behind the scene, the website has a batch job connected to the warehouse
application that periodically updates the availability of the products. When we finally decide to buy the selected
product, we have updated information related to its actual availability. We could easily incur in a lost update, where
our credit card is being charged, while in reality there is no product left in the warehouse. Or, from the server
perspective if our conversation is stateful, we could override the update carried out by the warehouse batch job, still
paying for a product we would never receive and messing up the database consistency.

![](./images/lock/lost_update.png)

This is something that we could not resolve with strict serialization, since our long conversation on the web is
spanning multiple database transactions and a variable, possible long, period of time.
This is the perfect case for optimistic locking with `@Version`; essentially we are placing in our basket a product with
an associated version. If, at the moment of purchase, we the update is fired, the version of the product at the database
is different, it means that the warehouse has somehow updated that record and we will receive an
`OptimisticLockException`

![](./images/lock/version.png)

Care must be taken when performing bulk updated since we need to explicit state that we want to update the version,
contrary to the single row update where hibernate automatically takes care of the version update.

### Scaling optimistic locking

Even using MVCC `@Version` can escalate in long conversations and become easily costly if multiple versions have to be
maintained. If we have a highly concurrent environment, where multiple users are, for example, getting a post and later
on try to update it, we will incur in the situation where only one update will be persisted, updating the version of the
record, and consequently making all the other users transactions fail due to a `StaleObjectStateException`.

One idea to improve the concurrency is to split a large table in smaller ones; a post could be divided into a post,
post_like and post_view tables, each maintaining its own version. This allows for a better scaling of writes, both
because we are insisting on different tables and also because these might easily have a different frequency of access.

Another Hibernate-specific approach is to use the versions-less optimistic locking with :

```java

@Entity
@Table
@DynamicUpdate
@OptimisticLocking(type = OptimisticLockType.DIRTY)
public class Post {
    ...
}
```

Essentially, hibernates add a WHERE clause to the update statement for each field of the record that is modified.
The WHERE clause is set equal to the state of the field when the record was first loaded; if the update returns a count
of 1 it means that the specific field has not been modified by others, in other words, the specific part of the record
we want to update has not changed since we loaded it.

An example:

* loading a post with id 1 and title 'jdbc'
* changing the title to 'jpa'
* hibernates fire and update that looks like

```sql
    UPDATE post
    SET title 'jpa'
    WHERE id = 1
      and title = 'jdbc'
```

* if the update count is 1, the entity was found with the same title that had when loaded; hence the operation is ok
* if the update count is 0, it means that in the meantime the entity field 'title' was changed by others, so preventing
  a lost update

### Explicit Optimistic locking modes

In JPA, there are also optimistic locks that can be called explicitly but are not the default behaviour.

* `LockModeType.OPTIMISTIC`: we want to check if the record we loaded is still the same by the time we are going to
  issue an update. Essentially, when the transaction commits, hibernate is going to check if the version of the record
  hasn't changed, if not an `OptimisticLockException` will be thrown. However, this only reduces the probability of a
  race condition (lost update) by shortening the window in which the race condition can happen since the application
  version check and the transaction commit are not atomic operation. One way to enforce the correct behaviour is to take
  a `LockModeType.PESSIMISTIC_READ` before issuing the update (e.g. I select a post in lock mode optimistic, create a
  new comment, and before committing the transaction I upgrade the lock on the post to pessimistic read, so that im sure
  that nothing can happen to the record until my transaction is committed).
* `LockModeType.OPTISMISTIC_FORCE_INCREMENT`: hibernates always increase the entity version and perform a version check
  upon transaction commit, ensuring repeatable reads. This is how a subversion system works (git): every read needs to
  take a force increment lock, the first commit increases the version of the repository, if other commits come later
  they will be rejected (OptimisticLockException) and require a merge.
* `LockModeType.PESSIMISTIC_FORCE_INCREMENT`: like the optimistic counterpart, but the first user that gets the lock
  will make others wait for the row level lock to be freed

At the application level, different locking strategies can be mixed to create case-specific algorithm that, for example,
avoids conflict in an entire root of tables (post, post_comment and post_comment_detail, for example).
For example, Hibernate allows defining custom event listener that can be appended to the existing ones and tied to
specific operations.

**N.B. see Vlad `OptimisticLockingChildUpdatesRootVersionTest` for implementation info**

---

# Database Caching

## Caching layers

Caching can happen in multiple layers, the discriminant is performance vs consistency. The more the cache is closer to
the database layer, the more consistent will be since the window of synchronisation between cache and db is minimal.
Vice versa, an application-level cache will be much faster since it will work in memory but will be much harder to
ensure consistency.

![](./images/cache/layers.png)

### Database and OS cache

Database cache (and OS cache) is something that we have out-of-the box, we only need to configure it properly. Databases
are meant to leverage the OS cache or to bypass it, depending on how they are structured; most of them use an internal
caching mechanism to speed up read and write operations. Operations on disk are inherently slow, even with ssd,
therefore, it is mandatory for a database to use the os cache or, more often the `in-memory buffer` to store and enqueue
pages and indexes. Changes are performed directly on the in-memory copy of a table page and only periodically
synchronised to disk.

#### Postgresql caching

Postgresql has a totally different caching mechanism, compared to other database vendors like oracle and SQL server;
While other databases try to store as much data as possible in the buffer pool, bypassing the OS cache, postgres doesn't
use direct I/O and has to rely on the OS cache.
It has double buffering: a `shared buffer`, limited in size, where it stores the most frequently accessed data, and on
this the modifications are performed and later synchronized to the disk at checkpoints, and also the OS cache where
everything else is cached.
The shared buffer stores 8KB table and index pages, and has a size that should be dimensioned between the 15–25% of the
available RAM (the rest of the RAM is devoted to OS cache)
If the shared buffer uses a `LFU` (Least Frequently Used) policy to prevent a full-table scan from replacing tha
frequently accessed pages, the OS cache uses a `LRU` (Least Recently Used) page eviction policy instead.

## Application-level caching

Database caching is essential and highly consistent (a modification in the database is almost immediately
reflected into the cache). However, usually it is not enough since some execution plans are CPU or Memory intensive
(like sorting, recursive queries, JSON processing, etc.) and the Buffer Pool is no longer sufficient. When reading,
for example, a multi-join that generates a big resultset, it is much more convenient to store it in the
application-level cache and be able to fetch it in O(1). Other advantages are: the database traffic is reduced,
decreasing the change of contention on the resources and therefore avoiding possible increasing response time;
if a significant portion of the dataset can be stored in the cache, even if in read-only mode, the application could
still be able to run even if there is a system failure or if the db is down for maintenance, therefore increasing
availability (stackoverflow, for example, use this approach since it has everything on premises and most of their user
reads data and few writes).
Nowadays, the typical application-level cache solutions (Redis, Memcached, Hazelcast, etc.) adopt a distributed
key-value stores, where the value can be anything, from an entire object to a set of relations. Like databases, these
solutions can still use replicas or shard to increase availability.

The performance gain comes from the fact that we are bypassing the database entirely but at the price of consistency,
meaning that it will be more difficult to synchronize the cache with the database (source of truth). Synchronization is
essential to ensure that what we read from the cache is a faithful representation of the current db state. To do this,
we have to be sure that every insert, update or delete is reflected in the cache; this process has several techniques
that go under the name of cache synchronization.

## Cache synchronization

Cache synchronization can be approached with different techniques, which aim to guarantee, synchronously or
asynchronously the consistency between cache and db.

### Cache-aside

Cache-aside is probably the most common approach; the application uses the cache as if it were the database itself, so
it first tries to get the data hitting the cache key, if it doesn't get a value in return, we have a `cache miss` and
therefore the application will need to ask the data directly to the database. If the element is present in the cache, we
will have a `cache hit` and if and update/deleted is performed the operation will be first performed on the database,
followed by the cache update on the existing key.

Since most key-values stores don't rely on the same transactions on which the database works on, stale cache entries can
be either:

* `invalidated` synchronously, marking the entry as stale but with the risk of marking as stale entries even if a db
  transaction rolls back
* `updated asynchronously` using `CDC` (Change Data Capture) tools at the cost of having (even if minimum) a time-lag
  between the changes at db and their propagation to the cache store.

#### CDC Change Data Capture

CDC is a technique that consists of recording the changes in an audit log that will serve as the source for updating the
cache asynchronously. The audit log can have different for and be created both with triggers or directly by parsing the
database `Redo log` which already contains the information from every commited transaction. N.B. the redo log is in
binary form, therefore, specific tools are needed to parse and interpret it). The problem with using asynchronous cache
synchronization is that we can only get `eventual consistency` but there will be always an instant in time where the
source of truth (the database) and the cache store are misaligned.

`Debezium` (from Redhat) is a common CDC solution that scans the redo log (write-ahead log `WAL` for postgres) through a
connector and propagate the changes to the application or to a listener like a kafka topic. Then, a consumer process can
consume the topic and operate based on the log entries for updating the cache.

#### Cache ripple effect

A downside of loading an entire relationship into the cache that can comprise thousands or millions of records is that
an update on a central entity (like a board in a social application) can impact the entire cache. At this point, we
don't have SQL in our aid to perform bulk updates, we need instead to manually invalidate all the entries or update them
in some way, and this can require a lot of time. A solution to mitigate this problem is to brake the cache in multiple
aggregation so that entries are not so interconnected.

## Hibernate Second-Level cache

While the database cache sits very close to the data and therefore provides high consistency but slow performance, the
application-level cache is the opposite.
In between, we have a third option, represented by the Hibernate second-level cache. Imagine having a single primary
node, used to handle the read-write transactions traffic, and a series of replicas that handle all the read requests.
If the read requests increase, we can easily horizontally scale the replica layer, adding nodes; but if a higher write
throughput is required, the only solution to maintain a single primary node (without incurring in the complexity of
having multiple primary nodes) is to scale it vertically, providing better hardware.

The second-level cache can offload the primary node read-write traffic by loading data before they actually needs to be
modified (hence diminishing the read in the read-write transaction).
Hibernate offers multiple second-level cache options, depending on the type of data we want to store:

* entity cache
* collection cache
* query cache
* natural id cache

When loading an entity, Hibernate will first look into the persistence context if there is already an entity in session
it will retrieve it with the same identifier; if not, the second-level cache will be queried and only then, if it is not
found, it will be fetched directly from the database.

![](./images/cache/2level.png)

The second-level cache is enabled by default with the property `hibernate.cache.use_second_level_cache` but it won't
operate unless a `RegionFactory` is provided. The most popular region cache is `ehcache` and can be enabled by setting
the property `hibernate.cache.region.factory_class`.
Entities can be annotated, specifying the concurrency strategy with
`@Cache(usage= CacheConcurrencyStratery.READ_WRITE)`. The second-level cache usually loads the entity loaded state,
an array from which the entity can be reconstructed.
For `@Immutable` entities (i.e., with a concurrency strategy of READ_ONLY) we can instruct Hibernate also to directly
store the entity reference, avoiding the penalty of reconstructing an entity from the loaded state. However, the entity
cannot have associations (there can only be basic attributes), limiting the usage of this feature.

If we are caching collections, we need to know that the second level cache only stores the identifiers of the child
entity in a given collection. Therefore, to be sure to not incur in an N+1 query issue, it is mandatory that the child
entities are cached as well.



---

# Connections

The throughput X is considered and the number of transactions per second and its reciprocal T_avg is the average
response time

![throughput.png](./images/connections/throughput.png)

The response time is a combination of several factors:

- acquire database connection
- statement submission to the database engine
- statements execution time
- resultset fetching
- closing transactions and releasing the connection

![response-time.png](./images/connections/response-time.png)

The most demanding operation is connection acquisition. The JDBC driver manager acts as a factory of physical database
connection; when the application asks for a new connection from the driver, a socket is opened, and a TCP connection is
established between the JDBC client and the database server (the DB will allocate a thread or a process).

![connection-lifecycle.png](./images/connections/connection-lifecycle.png)

This is why we use connection pools like HikariCP which leave the physical connection open while serving pool
connections that can be reused with a small overhead. Even closing a connection pool is not an expensive operation.

![pooled-connection.png](./images/connections/pooled-connection.png)

Hibernate DatasourceConnectionProvider is the best choice among the connection pool providers since it offers the best
control over the DataSource configuration, it supports JTA transactions (for Java EE projects), it can have as many
proxies as we want chained (like FlexyPool for monitoring), supports also connections pool not supported natively by
Hibernate. What Hibernate sees is just a decorated Datasource.

<img alt="datasource-provider.png" height="200" src="./images/connections/datasource-provider.png" width="600"/>

---

# Persistence Context in JPA and Hibernate

The persistence is responsible for managing entities once fetched from the database; we can think it as a Map where the
key is the entity identifier, and the values is the entity object reference. Its role is to synchronize the entities
state change with the database.

![](./images/persistence-context/api.png)

JPA offers the `EntityManager` interface to interact with the underlying persistence context, while Hibernate, which
predates JPA, offer the `Session interface` with the same role.

![](./images/persistence-context/entity_manager.png)

Since Hibernate 5.2 the `Session` interface directly implements the `EntityManager` specifications and therefore, its
implementation, the `SessionImpl` is directly related as well. These are commonly referred to as `first-level cache`

## Caching

Once an entity is *managed* (i.e. loaded) by the persistence context, it is also cached, meaning that each successive
request will avoid a database roundtrip.

The standard caching mechanism offered by the persistence context is the so called `write-behinde` cache mechanism;
basically the cache act as buffer, the write operations are not executed when fired but enqueued and scheduled for
execution. It will be only at flush time when all enqueued operations are executed and the cache state synchronized with
the database. This allows for the write operations to be batched together, reducing the number of round-trips between
application and database.

## Entity state transitions

Aside from caching entities, the persistence context manages entity state transitions; JPA and Hibernates define
slightly different methods in their respective interfaces to handle state transitions.

### JPA EntityManager

![](./images/persistence-context/jpa_transitions.png)

* A new entity when created for the first time is in the `New` or `Transiet` state; by calling `persist` it goes into
  `Managed` state; only at flush time an INSERT statement will be executed.
* By calling `find` (or any other retrieval method), an entity will be loaded into the persistence context in the
  `Managed` state
* By calling `remove` on a manged entity, the entity state will change to `Removed` and a flush time this will result in
  a DELETE statement being fired to delete the associated row in the table
* If the persistence context is closed ot the entity in managed state is evicted from it, the entity state will change
  to `Detached` meaning that it is no longer synchronized with the database.
* To reattach a detached entity, the `merge` method must be called and, if in the persistence context there
  isn't another managed entity with the same identifier, the persistence context will fetch the entity directly from the
  database and copy on it the state of the previously detached entity;
* There is no method in the JPA EntityManager that results in an UPDATE SQL statement; this is because at flush time,
  any entity in the `Managed` state will be synchronized with the database. If the persistence context determines the
  entity changed since it was first loaded (aka `dirty checking`), then it will trigger an UPDATE statement at flush
  time.

### Hibernate Session

Hibernate session adheres to the JPA standards but pre-dated it, therefore, even if the same methods are supported,
there
are some differences as well

![](./images/persistence-context/Hibernate_transitions.png)

* The `save` method is legacy, and unlike persist it returns the entity identifier
* The fetching can be done not only by entity identifier but also by `naturalId`
* The `delete` method is also a legacy one; as a matter of fact, the JPA `remove` delegates to the Hibernate `delete`
  method
* To reattach a detached entity, there is also the `update` method in addition to the JPA `merge`; this will change the
  entity state to `Managed` and schedule an UPDATE statement for the next flush operation

#### JPA merge vs Hibernate update

There is a slight difference in the behavior of JPA `merge` and Hibernate `update` methods, particularly important when
using batching. Both are used to reattach a detached entity to the persistence context and to eventually propagate the
UPDATE statement; however, JPA `merge` executes a SELECT statement for each entity that we need to reattach while
Hibernate `update` is more efficient since it simply reattaches the detached entity without the need of N SELECT
statements.

## Dirty checking

Dirty checking is the process of detecting entity modification happened in the persistence context; it facilitates
greatly the operations needed at the application level since the developer can focus on the domain models state changes
and leave to the persistence context the generation of the underlying SQL statements.

![](./images/persistence-context/dirty_checking.png)

When the persistence context is flushed, the Hibernate Session triggers a `FlushEvent`, handled by its default event
listener (`DefaultFlushEventListener`); For each managed entity a `FlushEntityEvent`  is triggered, handled by the
associated event listener (`DefaultFlushEntityEventListener`) which in turn calls the `findDirty` method on the
associated `EntityPersister`. The latter, for every entity attribute checks if the current value is changed since the
entity was first loaded in the persistence context; finally, the dirty properties are sent back to the
`FlushEntityEvent` that will schedule the required UPDATE statements.

We can conclude that the number of dirty checks is proportional to the number of entities loaded in the persistence
context, multiplied by their properties; since even if only one entity has changed, Hibernate will scan the entire
context, and this can have a significant impact on CPU resources, particularly if the number of managed entities is
large.

To limit this issue, we could rely on the Hibernate-specific annotation `@DynamicUpdate` which limits the update to the
columns that have effectively changed from their first fetch from the persistence context. This, however, will
automatically disable batching, even if a batch size is set.

### Bytecode enhancement

It is possible to activate, at build time as a maven plugin, the Hibernate bytecode enhancer which will allow you to
Hibernate to modify the bytecode of our java class for specific needs. In the specific case, we are interested in the
dirty tracking capability of the tool. Essentially, the Hibernate enhanced class will be able to track before flushing
all the changes in the entity properties and mark them for dirty checking using specific getters and setters; In this
way, at flush time the persistence context won't need to perform the computation required for dirty checking; instead,
it will simply ask the entity to return its dirty properties since the entity already holds the states of the changed
properties and their name/column.

**N.B. the difference in performance needs to be measured in context, and in general it will have significant effect
only when the size of the persistence context is significant.**

```xml

<plugin>
    <groupId>org.Hibernate.orm.tooling</groupId>
    <artifactId>Hibernate-enhance-maven-plugin</artifactId>
    <version>${Hibernate.version}</version>
    <executions>
        <execution>
            <configuration>
                <failOnError>true</failOnError>
                <enableDirtyTracking>true</enableDirtyTracking>
            </configuration>
            <goals>
                <goal>enhance</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Hydration -> read-by-name (Hibernate < 6.0)

When an entity is fetched from the database, the `EntityPersister` use the JDBC ResultSet to generate a Java `Object[]`
to store all entity property values; this operation is called `hydration`. Once the state is loaded, it's stored in the
persistence context along with the entity. This means that we need twice as much memory to manage an entity. In the
application layer, if we know that a specific entity is not to be modified, we can save space fetching the entity in
read-only mode. This can be done at Session level (`session.setDefaultReadOnly(true)`) or ar query level using hints (
`.setHint(QueryHints.HINT_READONLY, true)`).

**N.B. read-only queries optimize both memory (no hydration) and CPU (no dirty checking) resources.**

From Hibernate > 6.0, mapping the readings from JDBC has been fundamentally changed from a `read-by-name` to a [
`read-by-position`](https://docs.jboss.org/Hibernate/orm/6.0/migration-guide/migration-guide.html#type) approach,

## Flushing

Flushing is the act of synchronization between the in-memory information held by the persistence context and the
underlying database. The persistence context can be flushed either manually or automatically, as a matter of fact, both
the JPA and the Hibernate interfaces define the `flush` method to synchronize the in-memory domain models with the
underlying database structure. Flush is especially important before running a query or before a transaction commit since
it guarantees that the in-memory changes are visible; thi prevents [
`read-your-writes`](https://arpitbhayani.me/blogs/read-your-write-consistency/) consistency issue.

**JPA flushing modes**

![](./images/persistence-context/jpa_flush.png)

The `COMMIT` flush mode type is prone to inconsistency since it doesn't trigger a flush before every query that may not
capture the pending entity state changes

**Hibernate flushing modes**

![](./images/persistence-context/Hibernate_flush.png)

The `COMMIT` flush mode type is prone to inconsistency since it doesn't trigger a flush before every query that may not
capture the pending entity state changes

### AUTO flushing mode

![](./images/persistence-context/auto_flush.png)

JPA and Hibernate AUTO flush modes differ slightly; JPA requires a flush before each query and transaction while
Hibernate use a smarter approach (see `NativeHibernateSessionFactory`, trying to identify if the flush before the query
execution is required. To do so, Hibernate inspects the query table space affected by the incoming query, and it
triggers a flush only if there is an entity in a state transition in that same query table space. This is to delay as
much as possible the first-level cache (aka persistence context) synchronization.

The problem with Hibernate optimization is that it doesn't work with a native query out of the box since,
when a query is tagged as native, Hibernate knows that it holds the specific dialect of the underlying database
provider. Therefore, it won't parse it (for this reason, the JPA compliant implementation of the Hibernate session will
force a flush when it sees a native query, to be sure to maintain consistency). This results in Hibernate being unable
to know the query space of the incoming query. It is the developer job to instruct the query with the table space that
needs to synchronize upon its execution. (
see [Hibernate-query-space](https://thorben-janssen.com/Hibernate-query-spaces/)).

An alternative is to switch to `FlushMode.ALWAYS`, which has the same behavior of the JPA `AUTO`, either at session
level or only for the specific query.

### Flushing in batch processing

For standard operations, to avoid long locking time and excessive database memory consumption, JPA allows the
persistence context to span over multiple database transactions; however, in batch processing it is very important to
keep the persistence context within a reasonable dimension to avoid committing a single huge transaction that also
might fail at the end, rollback, and invalidate all the work done. To avoid this, it's not enough to periodically flush
anc clear the persistence context. However, we need also to commit the currently running database transaction to avoid a
single huge transaction at the end that either commits or fail and rollback.

These steps are defined as `flush-clear-commit`:

```java
private void flush(EntityManager entityManager) {
    //Commit triggers a flush, when using FlushType.AUTO, hence the SQL statements batched are executed
    entityManager.getTransaction().commit();
    entityManager.getTransaction().begin();
    entityManager.clear();
}
```

## Events and event listener

Hibernates internals defines, for any entity state change, specif events (i.e., `PersistEvent`, `MergeEvent` etc...)
associated with a default implementation of an event listener like `DefaultPersistEventListener` (these can be by custom
implementations). In turn, the event listener translates the state change in an internal `EntityAction` that can be
queued in an `ActionQueue` and gets executed only at flush time. If an entity that is going to be removed has an
association is marked with the `orphan removal strategy`, then the `EntityDeleteAction` at flush time can also generate
an `OrphanRemovalAction` if the child entity is unreferenced; both the actions trigger a SQL DELETE statement.

![](./images/persistence-context/events.png)

Toward the end of the flushing of the persistence context, Hibernate will execute all the actions that have been
enqueued, but in a strict specific order:

* `OrphanRemovalAction`
* `EntityInsertAction` and `EntityIdentityInsertAction`
* `EntityUpdateAction`
* `CollectionRemoveAction`
* `CollectionUpdateAction`
* `CollectionRecreateAction`
* `EntityDeleteAction`

This implies that, the order of operations defined at the application level is not what then Hibernate executes, unless
we force a flush. For example if we remove an entity with a unique column and in the same context we create a new one
with the same value for that unique field, we will incur in a `ConstrainViolationException` since as seen above, the
delete action is the last executed by Hibernate action queues, therefore he will try to create the new entity before
deleting the older one. The solution would be or to flush right after the calling of the remove (wrong approach) or to
make Hibernate fire an update statement by simply changing the existing entity instead of deleting it and recreating it.

**N.B avoiding manual flush we delay the connection acquisition and consequently reduce the transaction response time**

---

# SQL Statements: lifecycle, execution plan and caching

SQL is a declarative language, it "only" describes what we as clients want and not how the underlying database engine
will ingest the statement and produces the algorithms to retrieve the correct information. In this way, the database can
test different execution strategies and estimate which is the most efficient data access plan for the client needs.

![](./images/persistence-context/statements.png)

The main modules responsible for processing the SQL statements are the `Parser`, the `Optimizer` and the `Executor`.
The `Parser` verifies that the SQL statement is both syntactically and semantically correct (i.e., that both the
specific
SQL grammar is correct and that the referenced tables and columns exist). The result of the parsing phase is the
`syntax tree` (also known as query tree), i.e., the internal logical database representation of the query.

For a given syntax tree, the database must decide the most efficient data fetching algorithm; the operation of finding
the bests `action plans` is performed by the `Optimizer` which evaluates multiple data traversing options like which
access method (table scan or index scan), which joining strategy (nested loops, hash join or merge join) and the join
order. As a result, the Optimizer presents a list of access plans that will be passed to the Executor. The number of
action plan possible can be very large, depending on the complexity of the query, and it's a cost intensive operation
that can increase the transaction response time; therefore, the Optimizer has a fixed time budget for finding a
reasonable action plan, usually with the most common algorithm: the `Cost-Based optimizer`. In the end, the cost is
computed with the estimate of CPU cycle and I/O operation required for a specific plan. Due to the expensiveness of this
operation, most database vendors will cache the execution plan chosen but, since the database structure can change over
time, they also need a separate process for validating the existing plans.

Once the best execution plan has been chosen (and cached), the `Executor`, using the storing engine, will use it to
retrieve the data, built the resul set and, using the `trasaction engine`, guarantee the current transaction data
integrity.

## Execution plan cache

Both statement parsing and execution plan generation are expensive operation, therefore the statement string value is
used as an input to a hash function which becomes the key associated to the execution plan cache entry; as a
consequence, if the statement changes, the database cannot reuse the cached execution plan. A concrete example are the
dynamically generated JDBC statements.

## Prepared statement

![](./images/persistence-context/prepare.png)

Prepared statements, due to their static nature, allows the data access logic to reuse the same plan for multiple
execution since only the bind parameters are supposed to vary at runtime. Because the JDBC PreparedStatements take the
SQL query at creation time, the database can precompile (`prepare`) it in the syntax tree prior to executing it. During
the execution phase the driver sends the binding parameters values allowing the database to compile and run the
execution plan right away.

In PostgreSQL > 9.2 the `prepare` phase only parse and rewrite the statement while the optimization and the planning
phase are deferred until execution time; in this way the syntax tree is always optimized according to the actual values
of the binding parameters, leading to an optimal execution plan.

Theoretically, a prepared statement would require 2 database round trip, one for prepare and one for execute (contrary
to a plain statement); however the JDBC PreparedStatement is optimized to perform both the actions in a single database
request.

## Client-Side vs. Server-Side statement caching:

* Client-Side Prepared Statement: The JDBC driver simply performs parameter substitution and sends the query as a normal
  SQL command.
* Server-Side Prepared Statement: The query is sent to the PostgreSQL server as a prepared statement. This means the
  server parses and plans the query once and can then execute it repeatedly with different parameters more efficiently.

By default, when you use a prepared statement in Java, the driver doesn’t immediately create a prepared statement on the
PostgreSQL server. Instead, it first “simulates” a prepared statement on the client side. The driver keeps a count of
how many times that statement is executed. Once the same prepared statement has been run at least five times (this “5”
is the default value for the driver's prepare threshold), the driver then sends a command to the server to actually
create a server-side prepared statement.

---

# Fetching

## Fetching associations

By default, `@ManyToOne` and `OneToOne` associations use `FetchType.LAZY` (using a LEFT JOIN) while `@OneToMany` and
`@ManyToMany` use `FetchType.LAZY`. The options can be overridden both by changing the attribute in the mapping or at
query time by using an `entity graph`.

However, the fetching behavior when there is an eager fetch strategy is different whether we use a direct fetching (e.g.
`entityManager.find()`) or a JPQL query (e.g. `entityManager.createQuery()`; the direct fetching uses a LEFT JOIN while
for the JPQL query an additional query is performed to fetch the entity graph, even if not explicitly specified by the
query itself.

**N.B. Lazy fetching is only a hint, the underlying persistence provider might choose to ignore it**

The fetch strategy EAGER could be used also for retrieve collections, like in a `@OneToMany` relationship, but this is a
terrible idea.
Imagine a Post entity with a collection of comments and collection of tags; the problem is that the resulting query will
have a LEFT OUTER JOIN for both the comments and the tags, but tags and comments don't have any relationship between
them, therefore, the only way in SQL to join them is by a **cartesian product** that generates all the possible records
combination between the two tables (50 tags and 100 comments will generate 5000 rows). Slightly better would be if we
use a JPQL query since, instead of the cartesian product, we will have three queries generated, one for the Post SELECT,
one for the Tags (with an inner join supposing it's a `@ManyToMany` association) and one for the SELECT of Comments
matching the Post id.

### N+1 query problem

When using `FetchType.LAZY`, hibernates generates a proxy to represent the uninitialized association. Imagine fetching
three comments which have a lazy association with their Post parent entity. When first loaded, the Post property in each
comment is represented by a proxy object of type `Post.class` which has only the identifier given by the foreign key
coming from the Comment entity. At this point, if we try to access the Post, hibernate will execute and additional
SELECT query to initialize the Post proxy. Now imagine fetching a collection of `N` Comments and later access their Post
proxy in a for look or in a stream; the result will be that `N` SELECT queries will be performed to initialize the
proxies `+1` initial query to retrieve the Comments: hence `N+1` queries have been executed when an initial left join
would have solved the issue with just one query.

Be aware that an `N+1` situation can incur also if `FetchType.EAGER` is enabled; in fact, if we write a JPQL query
without the JOIN FETCH, hibernate will want to comply with the fetching strategy, therefore, one SELECT will be
performed as per JPQL statement, plus N queries to fetch all the associated entities.+

### Fetching multiple collections

While fetching multiple `@ManyToOne` and/or `OneToOne` association only requires multiple JOIN FETCH on the child
entities, without incurring in a cartesian product, fetching multiple collections at once can be more cumbersome.

Imagine we have a Post entity with a collection of Comments and a collections of Tags in a `OneToMany` and `@ManyToMany`
relationship respectively; now we have two scenarios trying to JOIN FETCH the collection in the same JPQL query, both
undesirable:

* If at least one of the collection is a List, we will incur in a `MultipleBagFetchException` an Hibernate exception
  telling us that it List doesn't have a built-in mechanism to avoid duplicates (the ones that will eventually occur due
  to the cartesian product)
* The query is executed, but the resultset contains a number of rows equal to the cartesian product of
  `Post * Comments * Tags` which, depending on the average size of these collections, is not optimal

The solution is quite simple, i.e. fetch only one collection per query in the same persistence context

## Open Session in View

**N.B. always disable the Hibernate properties, since for legacy reason is enabled by default in spring**

The Open Session in View is an architectural pattern that aims to hold the persistence context open throughout the whole
web request. This allows the service to provide entity without fetching the association, leaving to the UI the ability
to trigger the proxy if needed. The service layer is still responsible for managing the database transaction, but the
Session is no longer closed by the `HibernateTransactionManager`.

Viewed from a database prospective, there are a number of things that are undesirable:

* After the Service acquire the transaction (`getPosts()`), there is no active transaction, therefore, for the UI to ask
  for initialize an association a new database connection from the pool is required each time to execute a single fetch
  in auto-commit mode.
* Navigating uninitialized proxies can easily trigger a `N+1` query problem
* No separation of concerns since not only the service layer but also the view layer can access the persistence layer

![](./images/fetching/open-in-view.png)

**N.B. same as for Open session in View, Hibernate has a custom property to allow a similar behavior (i.e., fetching an
association after the persistence context is closed) and can be (BUT SHOULD NOT) be enabled through the property
`hibernate.enable_lazy_load_no_trans`**


---

# Projections

A projection is the operation of fetching a subset of an entity's columns and store it in a convenient POJO class.
Limiting the number of columns retrieved can be beneficial in terms of performance since only the data required by the
business case are fetched.

## JPA projections

By default, in plain JPA, a projection is represented by a Java `Object[]` where the selected columns, retrieved by the
`ResultSet` for each row, are stored in the order of the SELECT clause. This applies to any JPA Query, be it JPQL,
Criteria API or native SQL query

```java
List<Object[]> tuples = entityManager.createQuery("""
                select
                    p.id,
                    p.title
                from Post p
                """)
        .getResultList();
```

### Tuple

From JPA 2.0 the support for `Tuple` projections was added; Tuples container are essentially a map that store the column
name as key. One of the benefit is that we can access the records by column name instead of column position, therefore
it the latter changes there won't be any side effect on the application code. Like the Object array, also Tuples can be
used with any kind of query.

```java
List<Tuple> tuples = entityManager.createQuery("""
                select
                   p.id as id,
                   p.title as title
                from Post p
                """, Tuple.class)
        .getResultList();

long id = tuple.get("id", Number.class).longValue();
String title = tuple.get("title", String.class);
```

### DTO

The main disadvantage of `Objects[]` and `Tuples` is that the returned values are not typesafe; to solve this issue we
can use DTO projects that are essentially Java POJO class which maps only the desired columns. DTO class needs to have
field types that can be associated with the expected SQL types returned and a constructor matching the SELECT clause of
the query that is going to be projected.

```java
public class PostDTO {

    private final Long id;

    private final String title;

    public PostDTO(Number id, String title) {
        this.id = id.longValue(); // some database engine might return a BigInteger instead of a Long
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}

List<PostDTO> postDTOs = entityManager.createQuery("""
                select new com.vladmihalcea.hpjp.Hibernate.forum.dto.PostDTO(
                    p.id,
                    p.title
                )
                from Post p
                """, PostDTO.class)
        .getResultList();
```

By default, we need to reference the DTO projection with the full package name in the query; to solve this, improving
readability and allowing to move the DTOs from one package to another freely, we can use the Hibernate
`ClassImportIntegrator` to register our DTOs, by supplying the configuration to the `Hibernate.integrator_provider`.

```java
import java.util.Properties;

public void additionalProperties(Properties properties) {
    properties.put(
            "Hibernate.integrator_provider",
            (IntegratorProvider) () -> Collections.singletonList(
                    new ClassImportIntegrator(
                            List.of(
                                    PostDTO.class,
                                    PostRecord.class
                            )
                    ).excludePath("com.vladmihalcea.hpjp.Hibernate") // in case of conflicting DTOs name we can narrow down the Hibernate registration by specifying the base package path to exclude
            )
    );
}
```

By doing so we are able to use the simple class name of the DTO in the JPQL query.

#### mapping native SQL queries

However, DTO projects will work out of the box only with JPQL queries and not with native SQL queries. To work with the
latter there is a qute verbose fallback plan consisting in the use of a specific `@SqlResultSetMapping` and a
`@NamedNativeQuery` on top of the specific entity we want to map.

```java

@NamedNativeQuery(
        name = "PostDTONativeQuery",
        query = """
                SELECT
                   p.id AS id,
                   p.title AS title
                FROM post p
                """,
        resultSetMapping = "PostDTOMapping"
)
@SqlResultSetMapping(
        name = "PostDTOMapping",
        classes = @ConstructorResult(
                targetClass = PostDTO.class,
                columns = {
                        @ColumnResult(name = "id"),
                        @ColumnResult(name = "title")
                }
        )
)
@Entity(name = "Post")
@Table(name = "post")
public class Post {
    // ...
}
```

Where the SQL query needs to use the same column aliases that are expected by the `@ConstructorResult` mapping. To then
execute a named query, be it native or not, we use the entity manager `.createNamedQuery` method:

```java
var postDTOs = entityManager.createNamedQuery("PostDTOEntityQuery", PostDTO.class).getResultList();
```

**N.B. DTOs projection is perfectly suited for the use of Java Records as POJO**

## Hibernate projections

Prior to version 6, Hibernate allowed defining a custom `ResultTransformer` that use a DTO to projects a resultset by
its canonical constructor and java beans setter methods. Now there are alternatives (as of today, version 6.2 still
incubating) like `TupleTransfromer` and `ResultListTransformer` that can perform the same task but are quite messy (the
former is used to cast the JDBC resultset in the specific type of the destination DTO while the latter is used to filter
the result list that may contain duplicates since a cartesian product is performed when a one-to-may collection is
present).

## Bets approach for projecting parent-child relationship

As of today (February 2025) the best approach to project to DTO condensed information from a parent-child relationship
is to use interface projection. An interface is defined with the getters method related to the fields we need in the
projection; the resultset is then mapped into a proxy object

```java
interface PostWithCommentsProjection {
    Long getId();

    String getTitle();

    List<CommentProjection> getComments();

    interface CommentProjection {
        String getComment();
    }
}

class PostRepository {
    @EntityGraph(attributePaths = "comments")
    @Query("select p.id as id, p.title as title, c.comment as comment from Post p left join p.comments c ")
    List<PostWithCommentsProjection> findAllByProjecting();
}
```

The proxy can then be mapped to a concrete DTO and returned to the client.

___

# Batching in Hibernate

To enable batching in Hibernate, only a single property is required (while with plain JDBC a programmatic configuration
is required)

```yaml
Hibernate.jdbc.batch_size: 5
```

This setting is configured at the `EntityManagerFactory` (or `SessionFactory`) level so it will apply to all the
sessions the same batch size. From Hibernate 5.2 we can also set the jdbc batch size per query basis, optimizing each
business case.

```java
// Setting the batch size to null at the end of the method, will reset the entity manager configuration for the
// next usage of the extended entity manager

@PersistenceContext(type = PeristenceContextType.Extendend)
private EntityManager entityManager;

public void batchPerQuery() {
    entityManager.unwrap(Session.class).setJdbcBatchSize(10);
//...
    entityManager.unwrap(Session.class).setJdbcBatchSize(null);
}
```

If the entity identifier use the `GenerationType.IDENTITY`, Hibernate disable the batch insert since the only way to
know the entity id, needed to construct the first-level cache entry key, is to execute the actual INSERT statement.

**N.B. the restriction doesn't apply to UPDATE and DELETE statements that can still benefits of batch operation even
with the identity primary key**

## Bulking operations

Batching is not the only way to execute statements on multiple rows at once; SQL offers `bulk operations` to modify a
set of rows that satisfy a filtering criteria

```SQL
-- examples
UPDATE post
SET version = version + 1;
DELETE
FROM post
WHERE version > 1;
```

**N.B. operating on too many entities at once, especially in a highly concurrent environment, can be a problem both for
batching and bulk operations, since we are performing long-running transaction that will block any other write operation
**

## Batching in cascade

Imagine a parent entity with a `@OneToMany` mapping and `CascadeType.ALL` (e.g. post and post_comment); even if we
enable batch operations and try to insert multiple post with associated post_comments, Hibernate will execute separately
one insert statement for each entity persisted; this because JDBC batching requires executing the same
`PreparedStatement` over and over, but in this case the insert of a post in followed by the insert of a post_comment and
therefore the batch needs to be flushed prior to switching to the next post entity.

To solve this we need to enable another property that tells Hibernate to sort the type of statements while making sure
that the parent-child integrity is preserved.

```yaml
Hibernate.order_insert: true
Hibernate.order_updates: true
```

**N.B the same applies to batch UPDATE**

### DELETE cascade

Unlike INSERT and UPDATE statements, there is no property to sort DELETE statements in batch operations when cascading
deletes applies. However, there are some workarounds:

* delete all the child entities and then flux the persistence context before removing the parent entities
* bulk deleting the child entities (this implies to change the cascade type to only `PERSIST` and `MERGE` which has also
  the benefit of a faster flushing operation since the persistence context doesn't need to propagate the delete
  statement to the child entities)
* (BEST APPROACH) delegating the DELETE of the child entity to the database engine by adding a database-level directive
  of cascade delete on the foreign key
  ```SQL
  alter table post_comment
  add constraint fk_post_comment_post
  foreign key (post_id) references post on delete cascade
  ```

### Batching on versioned entity

Prior to Hibernate 5 or when using Oracle < 12c it was not possible to perform batch operations on entity with a
`@Version` field, since, due to some old JDBC driver logics, it would incur in an `OptimistickLockException` or
`StaleObjectStateException` due to a mismatch in the entity update count.

To solve this, since Hibernate 5 the property `Hibernate.jdbc.batch:versioned_data` is set to **true** by default.

## Default UPDATE behavior

The default UPDATE behavior consents to batch statements that modify different columns of the same entity since all the
columns are sent over the network, even those which haven't been modified. This leads to a wider possibility of batching
but with some potential disadvantages:

* if there are large columns (e.g. blob) we are sending these always over the network as well
* all indexes are scanned
* replication node will also be propagated with all the columns, not just those modified
* possible accidental execution of triggers

However, at the cost of disabling batching entirely for a given entity, we can mark it with the Hibernate annotation
`@DynamicUpdate` which will select only the modified columns over the network. This will disable batching because a
change in the binding parameters effectively results in a different prepared statement.

---

# Primary Keys and JPA identifiers

Primary keys are the unique identifier of a row in a table.

We can choose between a **natural ID**, i.e., a unique identifier naturally related to the entity we are storing, like a
social security number or a book ISBN. Natural identifiers are usually not the best choice due to their space overhead (
to be unique they are generally long).

The most common option is a **surrogate key**, i.e., a generic unique identifier. We can choose from:

- UUID (128bits)
- Auto-increment column (from 8 to 64 bits at max if we use long)

The dimension of the primary key and the efficiency of its associated index; b-trees are self-balancing tree data
structures at the core of relational databases, and they work better with sequential indexes because a new index is
always appended at the end of the clustered index, hence the physical ordering will match the logical ordering resulting
in an optimal key-set pagination (searching for a range of primary keys) since we will have sequential reads. If the key
is generated randomly, we will have fragmentation and page splits leading to more I/O operations.

## JPA identifiers

In JPA and Hibernate each entity requires an identifier for the primary key mapping. It can be manually assigned (using
only the `@Id`, annotation don’t do this) or generated by the provider with 3 different strategies:

- Identity → `GenerationType.IDENTITY`, using the physical database identity column. The identity generator can be
  applied only to a single column; An internal counter is incremented every time it is invoked using a lightweight
  locking mechanism that is not transactional (i.e., **rollbacks can lead to gaps in the identity column values of two
  consecutive columns rows**) and can release the lock right away after the increment.
  **DRAWBACKS:**
  The new value assigned from the counter is known only after executing the INSERT statement.
  Since the ID generation and the insert statement occur in a different transaction, Hibernate disables the batch
  insert. Hibernate issues the insert statement during the persist method call without waiting for the first-level
  cache (i.e., the Persistence Context) to flush and synchronize the entity state changes with the database.
- Sequence → `GenerationType.SEQUENCE`, using a sequence generator. A Sequence is a database object that generates a
  number upon incrementing an internal counter, and this can be done by incremental steps, allowing for
  application-level optimization techniques (like caching strategy to preallocate a set of values reducing the number of
  database round trip). The sequence call can be decoupled from the insert statement, allowing for batch insert.
  Like Identity columns, sequences use lightweight locks (released right after the increment operation) to prevent
  concurrent transactions from acquiring the same value, but since sequence increments are not transactional, gaps can
  be found in the primary key value of consecutive rows (not a bug).
- Table → `GenerationType.TABLE` (**DON’T USE IT,** best to use identity if sequences are not supported), to emulate
  a database sequence using a separate table (for database vendors that do not support sequences) with a low-level
  lock (row-level locking) that is transactional and requires the whole insert transaction to commit or rollback. An
  alternative is to have a separate transaction handling the value generation, but this requires a separate database
  connection

---

# Entity Relationship

## `@ManyToOne`

The `@ManyToOne` mapping is the most natural way to map a foreign key relationship between a parent and a child entity.
The annotation is placed on the child entity, usually with a LAZY behavior and optionally with a `@JoinColum` (mandatory
only if we use different naming in the entities' variables)

```java

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "post_id")
private Post post;
```

### bidirectional

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

## Unidirectional `@OneToMany`

(DON'T USE IT IF YOU CAN)

Even if uncommon, we might opt to hold a unidirectional reference only on the parent-side of the relationship.
Its performance depends on the implementation and on the type of collection we implement, but always worse (less
performant) than a bidirectional one.

### join table

One solution would be to have a join table acting as collector of the parent-child relationship, however this means to
have an extra table with two foreign keys (and most probably two indexes) instead of only one on the child side.

To map the collection on the parent-side we can use either a `Set` or a `List`

#### List vs Set Collections

While inserting elements in the two collections require the same effort, when coming to the remove of an element,
`Lists` are inefficients since they require the join table to remove all the rows associated with the id of the parent
entity to then re-add all the parent-child association rows except the one associated with the child entity we were
removing from the collection. To this, it must be added a re-balancing of the indexes in the join table two times, first
when the rows are removed, and then we are re-added.

If there is a meaning of ordering in the join column we could use the `List` collection together with the `@OrderColumn`
annotation to reduce the burden of using lists; in this way, if we want to remove the last element only two delete
statement are executed, one for the join table and one for the child table. However, if we are not removing the last
element, Hibernate will execute an update statement for each row that will be shifted.

### `@JoinColumn`

An alternative, that requires the child-entity to hold a reference to the parent, is to annotate the parent-side
collection with the `@JoinColumn` annotation. However, this approach is also inefficient since for persisting elements
in the parent side collection Hibernate will have to issue and insert statement and an update for each element
persisted. The update is required since the child entity is flushed before the parent-side collection, therefore,
Hibernate has no clue about the foreign key value, hence an update is required to set the foreign key. If the option
`nullable=false` is specified in the `@JoinColumn` annotation, Hibernate will flush the child entity with the foreign
key populated, but it will issue an update statement anyway.

Similarly, deleting an element from the parent-side collection has bad performance if the `nullable=false` is not set,
Hibernate will first fire an update statement on the child entity to set the foreign key value to null, and only after
is will issue the delete statement to remove that same child entity. If `nullable=false` we save the first useless
update statement.

## `@OneToOne`

### unidirectional

The one-to-one unidirectional relationship can be mapped in two ways, with a `@JoinColumn` or with a `@MapsId`
annotation. In the first case we would need two indexes on the child table, one for its primary key and one for the
foreign key pointing to the parent entity.

With `@MapsId` we have several advantages, parent and child table shares the same primary key, therefore, even if the
relation is unidirectional (from the child side) we can access the parent or the child knowing one id; the indexes on
the child side are reduced to one.

### bidirectional

If it is required to access the child entity even from the parent side, a `@OneToOne` annotation is required on the
parent-side. However, there is the possibility of incurring in an N+1 performance bottleneck: in fact, since Hibernate
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

## `@ManyToMany`

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

### Explicit mapping

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
Hibernate bag behavior seen in the unidirectional `@OnetoMany` mapping (i.e., we have a single delete statement instead
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

# JPA inheritance

Inheritance is a common paradigm in OOP to vary the behavior of a super-class (interface) depending on its subclasses'
implementation. With JPA inheritance we can implement behavioral design pattern such as the **Strategy Pattern** to
varying the business logic independently by the underlying database table.

In general, in a RDBMS, inheritance can only be emulated through table relationships

Martin Fowler design patter for RDBMS inheritance:

* **Single Table inheritance**: a single database table is used to represent all classes in a given inheritance
  hierarchy
* **Class Table inheritance**: the parent class and each subclass are mapped to separate tables related by the foreign
  key
  on the base class; the subclasses tables contains only the fields that are not in the parent class table.
* **Concrete Table inheritance**: each table in the hierarchy defines all attributes

JPA inheritance mapping models:

* `InheritanceType.SINGLE_TABLE`
* `InheritanceType.JOINED`
* `InheritanceType.TABLE_PER_CLASS`
* `@MappedSuperclass` (inheritance is available only in the Domain Model without being mirrored in the database)

## Single table inheritance

![](./images/inheritance/single-table.png)

Pros: query efficiency, since we have one single table to query

Cons: data integrity; we are not respecting the consistency principle of ACID since we can enforce non-nullability on
the application level (on the entities) but not on the persistence layer (since a single table represents more than one
entity there will be fields that are always null for one child entity but not the other, hence nullability can't be
constrained on the persistence layer). The only alternative is to use DB specifics constrain like CHECK (PostgreSQL) or
TRIGGER (MySQL)

### `@DiscriminatorColumn` and `@DiscriminatorValue`

When using single table inheritance, by default JPA use a discriminator column of type `String` called `DTYPE` to
differentiate the child entities of the single table with a discriminator value equal to the entity classes name.
However, this is not the only option since we can choose to use a custom string or opt to a char or an int. We only need
to annotate the parent entity with the `@DiscriminatorColumn`annotation specifying the type and the name of the column,
and then annotates all the child entities with the `@DiscriminatorValue`.

```java

@DiscriminatorColumn(
        discriminatorType = DiscriminatorType.STRING,
        name = "topic_type_id",
        columnDefinition = "VARCHAR(3)"
)
@DiscriminatorValue("TPC")
public class Topic {
    // ....
}
```

## Joined inheritance

![](./images/inheritance/join-table.png)

Pros: Explicit representation of the child entities and consistency in nullability

Cons: Expensive polymorphic queries due to the number of join

In the joined inheritance, the child entities have an explicit table that contains their specific properties while the
common attributes are defined in the parent table; child and parent entity share the same id column.
As a direct consequence, an insert of child entity requires the execution of two insert statements, one for the parent
and one for the child entity. While in single inheritance we have a single index (a single pkey) shared between parent
and child entities, the explicit child table representation requires the presence of more indexes. In contrast, joined
inheritance allows for consistency since we can respect nullability in subclasses both on the application and on the
persistence layer. Polymorphic queries are also more expensive since Hibernate needs to resolve all the possible
subclasses of the parent entity, leading to N + 1 joins where N is the number of subclasses, leading to a suboptimal
execution plan.

## Table per class

![](./images/inheritance/table-per-class.png)

N.B. Identity generation strategy is not allowed since it can't guarantee unique identifier between parent and children
entities and this will generate conflicts in polymorphic queries which needs a way to provide unique results

Pros: Write operation are faster since we are inserting only once in the specific subclass

Cons: Polymorphic queries use Hibernate `UNION ALL` in inner queries and therefore are very inefficient; besides, not
all Hibernates dialect support UNION ALL and fall back to UNION which adds a sorting phase to eliminate duplicates,
something that is redundant since polymorphic queries cannot contain duplicates since the entity identifier and the
discriminator column provides unique results in the inheritance tree.

In the table per class inheritance, the child entities contain all the fields that are shared with the parent entity
plus their specifics ones. There is no foreign key neither between parent and children.

## `@MappedSuperclass`

![](./images/inheritance/mapped.png)

Pros: Efficient read and write operations

Cons: No polymorphic queries or associations since there is no dedicated table for the parent class that now lives only
at the application level annotated with `@MappedSuperclass`

Using the `@MappedSuperclass` inheritance strategy, the persistence layer is represented only by the concrete
representations of the child entities. The parent entity is modeled for convenience only at the application level as
abstract class and owing the fields common to every member of the inheritance tree. Hence, no polymorphic queries are
possible, since the inheritance hierarchy exist only at the application level.

---

# EnumType

An EnumType can be mapped to a database column in 3 ways:

- Using JPA `@Enumerated` annotation:
    - with `EnumType.STRING` by which the enum is stored as a string. The string representation occupies more bits but
      it is human-readable

  ![string-enum.png](./images/enumtype/string-enum.png)

    - with `EnumType.ORDINAL` by which the enum is stored as an int representing the literal value. The ordinal
      representation saves bites but, for a service consuming this data, it doesn’t give any way to interpret the data
      without a decoding table. If we know the enum to have less than 256 values we can use a tinyint. To map the
      decoding table post_status_info we need a `@ManyToOne`association on the table containing the enum column,
      specifying that the item cannot be inserted or updated since we don't want to have two owner of the same data

  ![ordinal-enum-1.png](./images/enumtype/ordinal-enum-1.png)

  ![ordinal-enum-2.png](./images/enumtype/ordinal-enum-2.png)

- Creating a custom type (if the db vendor permits it) like the PostgreSQL EnumType, by which the database will be able
  to store the string value of the enum while reducing the space required in comparison to the varchar implementation
  required in EnumType.STRING

  ![psql-enum-create.png](./images/enumtype/psql-enum-create.png)

  Since Hibernate is not aware of the custom enum type we need to explicitly state its definition programmatically

  ![psql-enum.png](./images/enumtype/psql-enum.png)

  And create a custom class that extends the default Hibernate EnumType, overriding the nullSafeSet method that is
  responsible for binding the enum type as a jdbc-prepared statement parameter

  ![psql-custom-type.png](./images/enumtype/psql-custom-type.png)

---
