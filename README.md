# Java Persistence

Best practices in java/spring-jpa persistence
following Vlad Mihalcea courses https://vladmihalcea.com/courses/ and blog posts https://vladmihalcea.com/blog/

## Organization

- _**doc**_ folder contains theory summaries on the topics covered in the courses and blog posts
- _**entity-relationship**_ contains tests on the best and worst practices to code entities mapping. Separate dbs and
  changelogs are used to have a representative environment in each situation. N.B. be sure to activate the correct
  spring profile (it has to match the one requested in the context of each class).
