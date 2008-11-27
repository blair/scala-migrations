package com.imageworks.migration.tests.grant_and_revoke

import com.imageworks.migration.Migration

class Migrate_200811241940_CreateUser
  extends Migration
{
  def up : Unit =
  {
    // These commands configure Derby to turn on user authentication,
    // create users, and enable SQL authorization, needed for GRANT
    // statements to work.
    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.connection.requireAuthentication', 'true')""")

    // this cannot be undone
    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.database.sqlAuthorization', 'true')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.authentication.provider', 'BUILTIN')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.APP', 'password')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.test', 'password')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.database.fullAccessUsers', 'APP')""")

    create_table("location") { t =>
      t.varbinary("pk_location", PrimaryKey, Limit(16))
      t.varchar("name", Unique, Limit(255), NotNull)
    }
  }

  def down : Unit =
  {
    drop_table("location")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.test', null)""")
  }
}
