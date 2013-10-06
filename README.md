This document is a copy of http://code.google.com/p/scala-migrations/;
it may be easier to read it in a browser.

Scala Migrations is a library to manage upgrades and rollbacks to
database schemas.  Migrations allow a source control system to manage
together the database schema and the code using the schema.  It is
designed to allow multiple developers working on a project with a
database backend to design schema modifications independently, apply
the migrations to their local database for debugging and when
complete, check them into a source control system to manage as one
manages normal source code.  Other developers then check out the new
migrations and apply them to their local database.  Finally, the
migrations are used to migrate the production databases to the latest
schema version.

The Scala Migrations library is written in Scala and makes use of the
clean Scala language to write easy to understand migrations, which are
also written in Scala.  Scala Migrations provides a database
abstraction layer that allows migrations to target any supported
database vendor.

### History

Scala Migrations is developed at <a href="http://www.imageworks.com/">Sony Pictures Imageworks</a>
to manage database versioning for internal applications.  The design
is based off <a href="http://api.rubyonrails.org/classes/ActiveRecord/Migration.html">Ruby
on Rails Migrations</a> and in fact shares the exact same
`schema_migrations` table to manage the list of installed migrations.

### Sample Migration

Here is a migration used by !VnP3, an internal Imageworks project.

```scala
package com.imageworks.vnp.dao.migrations

import com.imageworks.migration.{Limit,
                                 Migration,
                                 Name,
                                 NotNull,
                                 OnDelete,
                                 Restrict,
                                 Unique}

/**
 * Create the 'facility_set_membership' table, which is a many-to-many
 * join table between the 'facility' and 'facility_set' tables.  It
 * represents the sets that a facility is a member of and the
 * facilities that are in a set.  Rows do not have a their own primary
 * key.
 */
class Migrate_20081216235329_FacilitySetMembership
  extends Migration
{
  val tableName = "facility_set_membership"

  def up() {
    createTable(tableName) { t =>
      t.varbinary("pk_facility", NotNull, Limit(16))
      t.varbinary("pk_facility_set", NotNull, Limit(16))
      t.bigint("created_micros", NotNull)
      t.bigint("modified_micros", NotNull)
    }

    // There should only be one pair of (pk_facility_set, pk_facility)
    // tuples in the entire table, i.e., for one facility set, the
    // facility should only appear once.
    addIndex(tableName,
             Array("pk_facility_set", "pk_facility"),
             Unique,
             Name("idx_fac_set_mmbrshp_uniq_pks"))

    addForeignKey(on(tableName -> "pk_facility"),
                  references("facility" -> "pk_facility"),
                  OnDelete(Restrict),
                  Name("fk_fac_set_mmbrshp_pk_fac"))

    addForeignKey(on(tableName -> "pk_facility_set"),
                  references("facility_set" -> "pk_facility_set"),
                  OnDelete(Restrict),
                  Name("fk_fac_set_mmbrshp_pk_fac_set"))
  }

  def down() {
    dropTable(tableName)
  }
}
```

To migrate a database to the latest version requires code similar to:

```scala
import com.imageworks.migration.{DatabaseAdapter,
                                 InstallAllMigrations,
                                 Vendor}

object Test
{
  def main(args: Array[String]) {
    val driver_class_name = "org.postgresql.Driver"
    val vendor = Vendor.forDriver(driver_class_name)
    val migration_adapter = DatabaseAdapter.forVendor(vendor, None)
    val data_source: javax.sql.DataSource = ...
    val migrator = new Migrator(data_source, migration_adapter)

    // Now apply all migrations that are in the
    // com.imageworks.vnp.dao.migrations package.
    migrator.migrate(InstallAllMigrations, "com.imageworks.vnp.dao.migrations", false)
  }
```

To rollback a database to its pristine state:

```scala
  migrator.migrate(RemoveAllMigrations, "com.imageworks.vnp.dao.migrations", false)
```

To rollback two migrations:

```scala
  migrator.migrate(RollbackMigration(2), "com.imageworks.vnp.dao.migrations", false)
```

And to migrate to a specific migration, rollbacking back migrations
that are newer than the requested migration version and installing
migrations older than the requested version.

```scala
  migrator.migrate(MigrateToVersion(20090731), "com.imageworks.vnp.dao.migrations", false)
```

### Supported Databases

Scala Migrations currently supports

* Derby
* MySQL
* Oracle
* PostgreSQL

Patches for other databases are welcome; however, you will need to
submit a [Contributor License Agreement](http://opensource.imageworks.com/cla/).

### Start using Scala Migrations

Maven Central hosts compiled jars for Scala 2.8.0 and greater,
compiled on JDK 1.6/JDBC 4.  All Scala Migrations artifacts have a
`groupId` of `com.imageworks.scala-migrations`.  A separate
compilation and publish is done for each Scala version, with a
distinct artifactId of the form `scala-migrations_X.Y.Z`, where
`X.Y.Z` is the Scala version used to compile Scala Migrations.

Direct links to jars compiled against 2.8.0 or greater can be found at
[Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.imageworks.scala-migrations).
Jars for Scala 2.7.7 for JDBC 3 and JDBC 4 are on the
[Downloads](http://code.google.com/p/scala-migrations/downloads/list) page.

#### sbt

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq("com.imageworks.scala-migrations" %% "scala-migrations" % "1.1.1")
```

#### Ivy

Add the following to the dependencies section of the `ivy.xml` file,
replacing `X.Y.Z` with your Scala version.

```xml
<dependency org="com.imageworks.scala-migrations" name="-migrations_X.Y.Z" rev="1.1.1" />
```

#### Maven

Add the following snippet to the `<dependencies />` section of the
project's `pom.xml` file, replacing `X.Y.Z` with your Scala version.

```xml
<dependency>
    <groupId>com.imageworks.scala-migrations</groupId>
    <artifactId>scala-migrations_X.Y.Z</artifactId>
    <version>1.1.1</version>
</dependency>
```

### Dependencies and Setup

Scala Migrations depends upon:

* The Simple Logging Facade for Java (SLF4J).

  http://www.slf4j.org/

  The Simple Logging Facade for Java or (SLF4J) serves as a simple
  facade or abstraction for various logging frameworks, e.g. log4j
  and java.util.logging, allowing the end user to plug in the
  desired logging framework at deployment time.

  Scala Migrations has a library dependency upon SLF4J's slf4j-api
  jar, which only provides an interface to a logging API.  The
  application must chose a concrete logging implementation by
  ensuring that one of the following jars is available in the
  classpath.  If no implementation jar is provided, the the
  no-operation logging implementation is used.

  * slf4j-log4j12

     Binding for log4j version 1.2, a widely used logging
     framework.  You also need to place log4j.jar on your
     classpath.

  * slf4j-jcl

     Binding for Jakarta Commons Logging.  This binding will
     delegate all SLF4J logging to JCL.

  * slf4j-jdk14

     Binding for java.util.logging, also referred to as JDK 1.4
     logging.

  * slf4j-nop

     Binding for NOP, silently discarding all logging.

  * slf4j-simple

     Binding for Simple implementation, which outputs all events to
     System.err.  Only messages of level INFO and higher are
     printed.  This binding may be useful in the context of small
     applications.

     See http://www.slf4j.org/manual.html for more information.

* The log4jdbc logging JDBC wrapper that logs all JDBC operations.

    http://code.google.com/p/log4jdbc/

    Since running a migration on a production database is dangerous
    operation that can leave irreversible damage if anything goes
    wrong, the JDBC connection given to all migrations is a log4jdbc
    `net.sf.log4jdbc.ConnectionSpy` that wraps the real connection.
    This logs all method calls so that any migration errors can be
    fully debugged.  log4jdbc uses SLF4J; see the log4jdbc website on
    how to set up the loggers and logging level for log4jdbc messages.

    As of 1.0.3, Scala Migrations will use log4jdbc to wrap the real
    database connection if log4jdbc is found at runtime in the
    classpath, otherwise it will use the raw database connection and
    not do any SQL specific logging.  No special work needs to be done
    by the migration author to use log4jdbc, besides making it
    available in the classpath.  Before 1.0.3, Scala Migrations
    required that log4jdbc be in the classpath.

### Migration Naming

In Scala Migrations, the migrations needs to be compiled and their
`*`.class files need to be made available at runtime; the source files
will not be available at runtime.

Scala Migrations then needs to know an ordering on the migrations, so
the timestamp needs to be in the class name.  Scala does not support
naming a symbol such as `20080717013526_YourMigrationName` because the
name begins with a digit (unless one were to quote the name which
would look odd), so the Scala Migrations looks for classes named

```
   Migrate_(\\d+)_([_a-zA-Z0-9]*)
```

The time stamp can be generated using the following command on Unix systems:

```
   $ date -u +%Y%m%d%H%M%S
```

This is different than Ruby on Rails migrations which are in filenames
of the form

```
   20080717013526_your_migration_name.rb
```

and have a corresponding class name such as `YourMigrationName`.  Ruby
on Rails can find all the migration `*`.rb files for a project and
load them at runtime and from the filename load the correct class
name.  The ordering to apply the migrations is contained in the
filename, not the class name.

### Unsupported Database Features

It is not a goal of Scala Migrations to check and report on the
compatibility of a Scala Migrations specific feature with a database.
For example, Oracle does not support the `"ON UPDATE SET NULL"` clause
on a foreign key constraint.  If a `OnUpdate(SetNull)` is specified
for a foreign key constraint, then Scala Migrations will generate that
clause and ask the database to execute it.

If Scala Migrations did attempt to check on the compatibility of each
feature, then it would need to grow much larger to know which features
worked on which database, and even worse, potentially know which
features appear in which database versions.  This is not something
that the authors of Scala Migrations want to maintain.

### Data Types

The following data types are supported listed with their mappings.  If
a database name is not specified, then the default mapping is used.
More information on the mappings is below.

* Bigint
  * Default: `BIGINT`
  * Oracle: `NUMBER(19, 0)`

* Blob
  * Default: `BLOB`
  * MySQL: `LONGBLOB`
  * PostgreSQL: `BYTEA`

* Boolean
  * Default: `BOOLEAN`
  * Derby: Unsupported; even though Derby 1.7 supports a `BOOLEAN`
    type, Scala Migrations currently always throws an
    `UnsupportedColumnTypeException`
  * Oracle: Unsupported; an `UnsupportedColumnTypeException` is
    thrown if Boolean is used

* Char
  * Default: `CHAR`

* Decimal
  * Default: `DECIMAL`
  * Oracle: `NUMBER`

* Integer
  * Default: `INTEGER`
  * Oracle: `NUMBER(10, 0)`

* Smallint
  * Default: `SMALLINT`
  * Oracle: `NUMBER(5, 0)`

* Timestamp
  * Default: `TIMESTAMP`
  * MySQL: `TIMESTAMP` but does not support fractional precision

* Varbinary
  * Default: `VARBINARY`
  * Derby: `VARCHAR FOR BIT DATA`
  * Oracle: `RAW`
  * PostgreSQL: `BYTEA`

* Varchar
  * Default: `VARCHAR`
  * Oracle: `VARCHAR2`

### Boolean Mapping

Scala Migrations does not define a mapping for the Boolean data type
in databases that do not have a native Boolean data type.  The reason
is that there are many ways of representing a Boolean value database
and Scala Migrations is not an ORM layer, so this decision is left to
the application developer.

Different representations that have been used in schemas include:

* A `CHAR(1)` column containing a 'Y' or 'N' value.  The column may
  have a `CHECK` constraint to ensure that the values are only 'Y'
  or 'N'.

* An `INTEGER` column with 0 representing to false and all other
  values representing true.

### BLOB and VARBINARY Mappings

Each database treats BLOB and VARBINARY differently.

| Database   | Scala Migrations Type   | SQL Type                | Maximum Length (bytes)   | Specify Length?   | Specify Default?   | References   | Notes   |
|:-----------|:------------------------|:------------------------|:-------------------------|:------------------|:-------------------|:-------------|:--------|
| Derby      | Blob                    | `BLOB`                  | 2,147,483,647            | Optional, defaults to 2 GB || No       | [1](http://db.apache.org/derby/docs/10.9/ref/rrefblob.html) | |
|            | Varbinary               | `VARCHAR FOR BIT DATA`  | 32,672                   | Required          | Yes                | [2](http://db.apache.org/derby/docs/10.9/ref/rrefsqlj32714.html) | |
| MySQL      | Blob                    | `LONGBLOB`              | 4,294,967,295            | No                | No                 | [3](http://dev.mysql.com/doc/refman/5.5/en/blob.html) | |
|            | Varbinary               | `VARBINARY`             | 21,844 >= && <= 65,535   | Required          | Yes                | [4](http://dev.mysql.com/doc/refman/5.5/en/storage-requirements.html) | Stored in row |
| Oracle     | Blob                    | `BLOB`                  | 4,294,967,296 in Oracle 8, larger in newer versions | No | ??     | [5](http://docs.oracle.com/cd/B28359_01/server.111/b28286/sql_elements001.htm#i54330) [6](http://ss64.com/ora/syntax-datatypes.html)| |
|            | Varbinary               | `RAW`                   | 2,000                    | Required          | ??                 | | |
| PostgreSQL | Blob                    | `BYTEA`                 | 1,073,741,823            | No                | Yes                | [7](http://www.postgresql.org/docs/9.1/static/storage-toast.html)| |
|            | Varbinary               | `BYTEA`                 | 1,073,741,823            | No                | Yes                || || ||

### Oracle and SMALLINT, INTEGER and BIGINT

Oracle does not have `SMALLINT`, `INTEGER` or `BIGINT` SQL types
comparable to other databases, such such as Derby, MySQL and
PostgreSQL.  These other databases used a fixed sized signed integer
with a limited range of values that can be stored in the column.

| Type     | Storage               | Min value           | rax value           |
|:---------|:----------------------|:--------------------|:--------------------|
| SMALLINT | 2-byte signed integer | -32768              | 32767               |
| INTEGER  | 4-byte signed integer | -2147483648         | 2147483647          |
| BIGINT   | 8-byte signed integer | -9223372036854775808| 9223372036854775807 |

Oracle does support an `INTEGER` column type but it uses a `NUMBER(38)`
to <a href="http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218">store it</a>.

On Oracle, a Scala Migration using any of the `SMALLINT`, `INTEGER`
and `BIGINT` types is mapped to a `NUMBER` with a precision smaller
than 38.

| Migration Type | Oracle Type   |
|:---------------|:--------------|
| SMALLINT       | NUMBER(5, 0)  |
| INTEGER        | NUMBER(10, 0) |
| BIGINT         | NUMBER(19, 0) |

This helps ensure the compatibility of any code running against an
Oracle database so that it does not assume it can use 38-digit integer
values in case the data needs to be exported to another database or if
the code needs to work with other databases.  Columns wishing to use a
`NUMBER(38)` should use a DecimalType column.

### NUMERIC and DECIMAL

There is a minor difference in the definition of the `NUMERIC` and
`DECIMAL` types according to the <a
href="http://www.contrib.andrew.cmu.edu/~shadow/sql/sql1992.txt">SQL
1992 standard</a>:

```
17) NUMERIC specifies the data type exact numeric, with the decimal
    precision and scale specified by the <precision> and <scale>.

18) DECIMAL specifies the data type exact numeric, with the decimal
    scale specified by the <scale> and the implementation-defined
    decimal precision equal to or greater than the value of the
    specified <precision>.
```

However, in practice, all databases we looked at implement them
identically.

* Derby

  "NUMERIC is a synonym for DECIMAL and behaves the same way. See
  DECIMAL data type."

  http://db.apache.org/derby/docs/10.4/ref/rrefsqlj12362.html

  http://db.apache.org/derby/docs/10.4/ref/rrefsqlj15260.html

* Mysql

  "NUMERIC implemented as DECIMAL."

  http://dev.mysql.com/doc/refman/5.1/en/numeric-types.html

* Oracle

  Only has the `NUMBER` type.

  http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm

  http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218

* PostgreSQL

  "The types decimal and numeric are equivalent. Both types are
  part of the SQL standard."

  The documentation uses `NUMERIC` more and lists `DECIMAL` as an alias.

  http://www.postgresql.org/docs/8.3/interactive/datatype-numeric.html

  http://www.postgresql.org/docs/8.3/interactive/datatype.html#DATATYPE-TABLE

### Auto-incrementing Column Default Values

Several databases natively support a default value for integer column
data types that use as the next default value the next value from an
automatically increasing sequence of integer values.  The use of the
AutoIncrement column option enables this feature for a column.

Here are the database mappings:

* Derby

  Only supported on `SMALLINT`, `INT` and `BIGINT` data types using
  Derby's `GENERATED BY DEFAULT AS IDENTITY`.  The alternate setting
  `GENERATED ALWAYS AS IDENTITY` is not used as it is not consistent
  with MySQL and PostgreSQL which permits the application to
  explicitly specify the column's value.

  http://db.apache.org/derby/docs/10.9/ref/rrefsqlj37836.html

* MySQL

  Only supported on `SMALLINT`, `INT` and `BIGINT` data types using
  MySQL's `AUTO_INCREMENT` keyword.

  http://dev.mysql.com/doc/refman/5.5/en/create-table.html
  http://dev.mysql.com/doc/refman/5.5/en/example-auto-increment.html

* PostgreSQL

  Only supported on `SMALLINT`, `INT` and `BIGINT` data types by
  replacing the data type name with `SMALLSERIAL`, `SERIAL` and
  `BIGSERIAL`, respectively.  Support for `SMALLSERIAL` is only
  available in PostgreSQL 9.2 and greater.

  http://www.postgresql.org/docs/9.2/static/datatype-numeric.html#DATATYPE-SERIAL

* Oracle

  No support is provided in this commit as it appears that
  equivalent functionality can only be provided by using triggers.

### Character Set Encoding

Scala Migrations supports specifying the character set for `Char` and
`Varchar` columns with the `CharacterSet()` column option, which takes
the name of the character set as an argument.  Currently, the only
supported character set name is Unicode.

Here is how different databases handle character set encoding.

* Derby

  "Character data types are represented as Unicode 2.0 sequences in
  Derby."

  So specifying `CharacterSet(Unicode)` does not change its
  behavior.  Using any character set name besides Unicode as the
  argument to `CharacterSet()` raises a warning and is ignored.

  http://db.apache.org/derby/docs/10.4/devguide/cdevcollation.html

* MySQL

  MySQL supports 30+ character sets and and all of them can be
  simultaneously used; in fact, a table can have multiple character
  type columns, each with a different character set.  See
  http://dev.mysql.com/doc/refman/5.5/en/charset-database.html for
  reference.

  If no `CharacterSet` is used, then MySQL will use the database's
  or the server's default character set and the default character
  set's default collation.  If `CharacterSet(Unicode)` is used, then
  Scala Migrations uses the `utf8` character set with the
  `utf8_unicode_ci` collation, which is not MySQL's default
  `utf8_general_ci` collation for `utf8`, as `utf8_unicode_ci` is
  [http://stackoverflow.com/questions/766809/ not incorrect].

  Users wishing to have more control on specifying character sets
  and collations can discuss this on the developers mailing list.

* PostgreSQL

  The character set encoding is chosen when a database is created
  with the "createdb" command line utility or the

```
   CREATE DATABASE ENCODING [=] encoding
```

  SQL statement.  So specifying any `CharacterSet` has no effect.

* Oracle

  Oracle only supports two character sets.  The first uses the
  database character set which was chosen when the database was
  created.  This encoding is used for `CHAR`, `VARCHAR2` and `CLOB`
  columns.  The second character set is called the national
  character set and is Unicode, which is used for `NCHAR`,
  `NVARCHAR2` and `NCLOB` columns.  There are two encodings
  available for the national character set, `AL16UTF16` and `UTF8`.
  By default, Oracle uses `AL16UTF16`.

  http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14225/ch6unicode.htm

  Specifying no `CharacterSet` column option defaults the `Char`
  type to `CHAR` and the `Varchar` type to `VARCHAR2`.  If
  `CharacterSet(Unicode)` is given, then `Char` uses `NCHAR` and
  `Varchar` uses `NVARCHAR2`.  Using any character set name besides
  `Unicode` as the argument to `CharacterSet()` raises a warning and
  is ignored, resulting in `CHAR` and `VARCHAR2` column types.

### Caveats

* Index and foreign key names do not use the same naming convention
  as the Ruby on Rails migrations, so a port of Ruby on Rails
  migrations to Scala Migrations should specify the index name using
  the `Name()` case class as an option to `add_index()` or
  `remove_index()`.
