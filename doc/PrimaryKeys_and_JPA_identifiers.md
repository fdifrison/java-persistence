# Primary Keys and JPA identifiers

Primary keys are the unique identifier of a row in a table.

We can choose between a **natural ID**, i.e. a unique identifier naturally related to the entity we are storing, like a
social security number or a book ISBN. Natural identifiers are usually not the best choice due to their space overhead (
to be unique they are generally long).

The most common option is a **surrogate key**, i.e. a generic unique identifier. We can choose from:

- UUID (128bits)
- Auto-increment column (from 8 to 64bits at max if we use long)

The dimension of the primary key and the efficiency of its associated index; b-trees are self-balancing tree data
structures at the core of relational databases and they work better with sequential indexes because a new index is
always appended at the end of the clustered index hence the physical ordering will match the logical ordering resulting
in an optimal key-set pagination (searching for a range of primary keys) since we will have sequential reads. If the key
is generated randomly we will have fragmentation and page splits leading to more I/O operations.

# JPA identifiers

In JPA and Hibernate each entity requires an identifier for the primary key mapping. It can be manually assigned (using
only the `@Id`, annotation don’t do this) or generated by the provider with 3 different strategies:

- Identity → `GenerationType.IDENTITY`, using the physical database identity column. The identity generator can be
  applied only to a single column; An internal counter is incremented every time it is invoked using a lightweight
  locking mechanism that is not transactional (i.e **rollbacks can lead to gaps in the identity column values of two
  consecutive columns rows**) and can release the lock right away after the increment.
  **DRAWBACKS:**
  The new value assigned from the counter is known only after executing the INSERT statement.
  Since the ID generation and the insert statement occur in a different transaction, Hibernate disables the batch
  insert. Hibernate issues the insert statement during the persist method call without waiting for the first-level
  cache (i.e. the Persistence Context) to flush and synchronize the entity state changes with the database.
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