package com.imageworks.migration.tests.grant_and_revoke

import com.imageworks.migration.{
  Migration,
  Options
}

class Migrate_200811261513_Grants
  extends Migration
{
  def up : Unit =
  {
    grant("location", "test", SelectPrivilege)
  }

  def down : Unit =
  {
    revoke("location", "test", SelectPrivilege)
  }
}
