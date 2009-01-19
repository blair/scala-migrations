package com.imageworks.migration.tests.scale_without_precision

import com.imageworks.migration.Migration

class Migrate_200812041647_Foo
  extends Migration
{
  def up : Unit =
  {
    create_table("foo") { t =>
      t.decimal("bar", Scale(3))
    }
  }

  def down : Unit = { }
}
