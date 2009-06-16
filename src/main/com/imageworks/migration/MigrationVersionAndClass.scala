package com.imageworks.migration

/**
 * A tuple-like class that holds the version number of a migration
 * implementation and the migration class.
 *
 * @param version the migration version
 * @param clazz the Migration subclass
 */
case class MigrationVersionAndClass(version : Long,
                                    clazz : Class[_ <: Migration])
  extends java.lang.Comparable[MigrationVersionAndClass]
  with scala.Ordered[MigrationVersionAndClass]
{
  override
  def compare(that : MigrationVersionAndClass) : Int =
  {
    val this_version = this.version
    val that_version = that.version

    if (this_version < that_version) {
      -1
    }
    else if (this_version > that_version) {
      1
    }
    else {
      this.clazz.getName.compareTo(that.clazz.getName)
    }
  }
}
