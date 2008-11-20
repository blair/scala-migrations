package com.imageworks.migration

sealed abstract class MigrationDirection
{
  /**
   * A human readable string representing the migration direction.
   */
  def str : String
}

case object Up extends MigrationDirection
{
  val str = "up"
}

case object Down extends MigrationDirection
{
  val str = "down"
}
