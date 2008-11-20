package com.imageworks.migration

/**
 * The set of migrator operations that can be performed.
 */
sealed abstract class MigratorOperation

/**
 * Install all available migrations.
 */
case object InstallAllMigrations
  extends MigratorOperation

/**
 * Remove all installed migrations.  This should effectively return
 * the database to a pristine state, except if any migration throws a
 * IrreversibleMigrationException.
 */
case object RemoveAllMigrations
  extends MigratorOperation

/**
 * Remove all migrations with versions greater than the given version
 * and install all migrations less then or equal to the given version.
 */
case class MigrateToVersion(version : Long)
  extends MigratorOperation

/**
 * Rollback 'count' migrations in the database.  This is different
 * than using MigrateToVersion to migrate to the same version, as
 * MigrateToVersion will also install any missing migration with a
 * version less then the target version.  This rollback operation only
 * removes migrations from the database.
 */
case class RollbackMigration(count : Int)
  extends MigratorOperation
{
  if (count < 1) {
    val message = "The number of migrations to rollback must be greater " +
                  "than zero."
    throw new IllegalArgumentException(message)
  }
}
