# JPA inheritance

Inheritance is a common paradigm in OOP to vary the behavior of a super-class (interface) depending on its subclasses'
implementation. With JPA inheritance we can implement behavioral design pattern such as the **Strategy Pattern** to
varying the business logic independently by the underlying database table.

In general, in a RDBMS, inheritance can only be emulated through table relationships

Martin Fowler design patter for RDBMS inheritance:

* **Single Table inheritance**: a single database table is used to represent all classes in a given inheritance hierarchy
* **Class Table inheritance**: the parent class and each subclass are mapped to separate tables related by the foreign key
  on the base class; the subclasses tables contains only the fields that are not in the parent class table.
* **Concrete Table inheritance**: each table in the hierarchy defines all attributes

JPA inheritance mapping models:

* `InheritanceType.SINGLE_TABLE`
* `InheritanceType.JOINED`
* `InheritanceType.TABLE_PER_CLASS`
* `@MappedSuperclass` (inheritance is available only in the Domain Model without being mirrored in the database)