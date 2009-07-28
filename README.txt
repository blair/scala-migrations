This project is a Scala port of the Ruby on Rails migrations, which
see

http://api.rubyonrails.org/classes/ActiveRecord/Migration.html

A sample Scala migration looks like

class Migrate_20081118201742_CreatePeopleTable
  extends Migration
{
  def up : Unit =
  {
    create_table("people") { t =>
      t.varbinary("pk_people", PrimaryKey, Limit(16))
      t.varbinary("pk_location", Limit(16), NotNull)
      t.integer("employee_id", Unique)
      t.integer("ssn", NotNull)
      t.varchar("first_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.char("middle_initial", Limit(1), Nullable, CharacterSet(Unicode))
      t.varchar("last_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.timestamp("birthdate", Limit(0), NotNull)
      t.smallint("height", NotNull, Check("height > 0"))
      t.smallint("weight", NotNull, Check("weight > 0"))
      t.integer("vacation_days", NotNull, Default("0"))
      t.bigint("hire_time_micros", NotNull)
      t.decimal("salary", Precision(7), Scale(2), Check("salary > 0"))
      t.blob("image")
    }

    add_index("people", "ssn", Unique)

    add_foreign_key(on("people" -> "pk_location"),
                    references("location" -> "pk_location"),
                    OnDelete(Cascade),
                    OnUpdate(Restrict))

    add_check(on("people" -> "vacation_days"),
              "vacation_days >= 0")
  }

  def down : Unit =
  {
    drop_table("people")
  }
}

The style of the Migration classes used for Scala Migrations is
similar to the Ruby on Rails migrations.  The implementation uses the
same schema_migrations table appearing in Ruby on Rails 2.1 and
migration naming convention to manage the list of migrations that have
been applied to the database.

SUPPORTED DATABASES
-------------------

Scala Migrations currently supports

  * Derby
  * Oracle
  * PostgreSQL

Patches for other databases are welcome.

MIGRATION NAMING
----------------

In Scala Migrations, the migrations needs to be compiled and their
*.class files need to be made available at runtime; the source files
will not be available at runtime.

Scala Migrations then needs to know an ordering on the migrations, so
the timestamp needs to be in the class name.  Scala does not support
naming a symbol such as 20080717013526_YourMigrationName because the
name begins with a digit (unless one were to quote the name which
would look odd), so the Scala Migrations looks for classes named

    Migrate_(\\d+)_([_a-zA-Z0-9]*)

The timestamp can be generated using the following command on Unix
systems:

    $ date -u +%Y%m%d%H%M%S

This is different than Ruby on Rails migrations which are in filenames
of the form

    20080717013526_your_migration_name.rb

and have a corresponding class name such as YourMigrationName.  Ruby
on Rails can find all the migration *.rb files for a project and load
them at runtime and from the filename load the correct class name.
The ordering to apply the migrations is contained in the filename, not
the class name.

UNSUPPORTED DATABASE FEATURES
-----------------------------

It is not a goal of Scala Migrations to check and report on the
compatibility of a Scala Migrations specific feature with a database.
For example, Oracle does not support the "ON UPDATE SET NULL" clause
on a foreign key constraint.  If a OnUpdate(SetNull) is specified for
a foreign key constraint, then Scala Migrations will generate that
clause and ask the database to execute it.

If Scala Migrations did attempt to check on the compatibility of each
feature, then it would need to grow much larger to know which features
worked on which database, and even worse, potentially know which
features appear in which database versions.  This is not something
that the authors of Scala Migrations want to maintain.

DATA TYPES
----------

The following data types are supported listed with their mappings.  If
a database name is not specified, then the default mapping is used.
More information on the mappings is below.

    Bigint
        Default: BIGINT
        Oracle: NUMBER(19, 0)
    Blob
        Default: BLOB
        PostgreSQL: BYTEA
    Boolean
        Default: BOOLEAN
        Derby: Unsupported
        Oracle: Unsupported
    Char
        Default: CHAR
    Decimal
        Default: DECIMAL
        Oracle: NUMBER
    Integer
        Default: INTEGER
        Oracle: NUMBER(10, 0)
    Smallint
        Default: SMALLINT
        Oracle: NUMBER(5, 0)
    Timestamp
        Default: TIMESTAMP
    Varbinary
        Default: None
        Derby: VARCHAR FOR BIT DATA
        Oracle: RAW
        PostgreSQL: BYTEA
    Varchar
        Default: VARCHAR
        Oracle: VARCHAR2

Boolean Mapping
---------------

Scala Migrations does not define a mapping for the Boolean datatype in
databases that do not have a native Boolean datatype.  The reason is
that there are many ways of representing a Boolean value database and
Scala Migrations is not an ORM layer, so this decision is left to the
application developer.

Different representations that have been used in schemas include:

1) A CHAR(1) column containing a 'Y' or 'N' value.  The column may
   have a CHECK constraint to ensure that the values are only 'Y' or
   'N'.

2) An INTEGER column with 0 representing to false and all other values
   representing true.

Oracle and SMALLINT, INTEGER and BIGINT
---------------------------------------

Oracle does not have SMALLINT, INTEGER or BIGINT SQL types comparable
to other databases, such such as Derby, MySQL and PostgreSQL.  These
other databases used a fixed sized signed integer with a limited range
of values that can be stored in the column.

  Type        Storage                           Min value            Max value
  ----------------------------------------------------------------------------
  SMALLINT    2-byte signed integer                -32768                32767
  INTEGER     4-byte signed integer           -2147483648           2147483647
  BIGINT      8-byte signed integer  -9223372036854775808  9223372036854775807

Oracle does support an "INTEGER" column type but it uses a NUMBER(38)
to store it:

http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218

On Oracle, a Scala Migration using any of the SMALLINT, INTEGER and
BIGINT types is mapped to a NUMBER with a precision smaller than 38.

  Migration Type    Oracle Type
  -------------------------------
  SMALLINT          NUMBER(5, 0)
  INTEGER           NUMBER(10, 0)
  BIGINT            NUMBER(19, 0)

This helps ensure the compatibility of any code running against an
Oracle database so that it does not assume it can use 38-digit integer
values in case the data needs to be exported to another database or if
the code needs to work with other databases.  Columns wishing to use a
NUMBER(38) should use a DecimalType column.

NUMERIC and DECIMAL
------------------

There is a minor difference in the definition of the NUMERIC and
DECIMAL types according to the SQL 1992 standard downloaded from

http://www.contrib.andrew.cmu.edu/~shadow/sql/sql1992.txt

  """
  17) NUMERIC specifies the data type exact numeric, with the decimal
      precision and scale specified by the <precision> and <scale>.

  18) DECIMAL specifies the data type exact numeric, with the decimal
      scale specified by the <scale> and the implementation-defined
      decimal precision equal to or greater than the value of the
      specified <precision>.
  """

However, in practice, all databases I looked at implement them
identically.

  *) Derby

     "NUMERIC is a synonym for DECIMAL and behaves the same way. See
     DECIMAL data type."

     http://db.apache.org/derby/docs/10.4/ref/rrefsqlj12362.html
     http://db.apache.org/derby/docs/10.4/ref/rrefsqlj15260.html

  *) Mysql

     "NUMERIC implemented as DECIMAL."

     http://dev.mysql.com/doc/refman/5.1/en/numeric-types.html

  *) Oracle

     Only has the NUMBER type.

     http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm

     http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218

  *) PostgreSQL

     "The types decimal and numeric are equivalent. Both types are
     part of the SQL standard."

     The docs use NUMERIC more and list DECIMAL as an alias.

     http://www.postgresql.org/docs/8.3/interactive/datatype-numeric.html
     http://www.postgresql.org/docs/8.3/interactive/datatype.html#DATATYPE-TABLE

CHARACTER SET ENCODING
----------------------

Scala Migrations supports specifying the character set for Char and
Varchar columns with the CharacterSet() column option, which takes the
name of the character set as an argument.  Currently, the only
supported character set name is Unicode.

Here is how different databases handle character set encoding.

 * Derby

   "Character data types are represented as Unicode 2.0 sequences in
   Derby."

   So specifying CharacterSet(Unicode) does not change its behavior.
   Using any character set name besides Unicode as the argument to
   CharacterSet() raises a warning and is ignored.

   http://db.apache.org/derby/docs/10.4/devguide/cdevcollation.html

* PostgreSQL

  The character set encoding is chosen when a database is created with
  the "createdb" command line utility or the

    CREATE DATABASE ENCODING [=] encoding

  SQL statement.

  So specifying any CharacterSet has no effect.

* MySQL

  MySQL supports specifying the character set on a per-column basis.

* Oracle

  Oracle only supports two character sets.  The first uses the
  database character set which was chosen when the database was
  created.  This encoding is used for CHAR, VARCHAR2 and CLOB columns.
  The second character set is called the national character set and is
  Unicode, which is used for NCHAR, NVARCHAR2, and NCLOB columns.
  There are two encodings available for the national character set,
  AL16UTF16 and UTF8.  By default, Oracle uses AL16UTF16.

  http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14225/ch6unicode.htm

  Specifying no CharacterSet column option defaults the Char type to
  CHAR and the Varchar type to VARCHAR2.  If CharacterSet(Unicode) is
  given, then Char uses NCHAR and Varchar uses NVARCHAR2.  Using any
  character set name besides Unicode as the argument to CharacterSet()
  raises a warning and is ignored, resulting in CHAR and VARCHAR2
  column types.

CAVATS
------

1) Index and foreign key names do not use the same naming convention
   as the Ruby on Rails migrations, so a port of Ruby on Rails
   migrations to Scala Migrations should specify the index name using
   the Name() case class as an option to add_index() or
   remove_index().
