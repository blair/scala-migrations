package com.imageworks.migration.tests.types

import com.imageworks.migration.Migration

class Migrate_20081212213908_CreateTypetestTable
  extends Migration
{
  def up : Unit =
  {
    create_table("types_test") { t =>
      // The binary column is not tested because its representation is
      // database dependent.

      t.bigint("bigint_column")
      t.blob("blob_column")
      t.char("char_column", Limit(4))
      t.decimal("decimal_column", Precision(22), Scale(2))
      t.integer("integer_column")
      t.timestamp("timestamp_column")
      t.varbinary("varbinary_column", Limit(4))
      t.varchar("varchar_column", Limit(4))
    }
  }

  def down : Unit =
  {
    drop_table("types_test")
  }
}
