package com.imageworks.migration

/**
 * Container for the state of all the available and installed
 * migrations.
 *
 * @param not_installed a sorted map of migration version numbers to
 *        Migration subclasses that are not installed in the database
 * @param installed_with_available_implementation a sorted map of
 *        migration version numbers to Migration subclasses that are
 *        currently installed in the database that have a matching a
 *        Migration subclass
 * @param installed_without_available_implementation a sorted set of
 *        migration version numbers that are currently installed in
 *        the database but do not have a matching a Migration subclass
 */
case class MigrationStatuses
  (not_installed : scala.collection.SortedMap[Long,Class[_ <: Migration]],
   installed_with_available_implementation : scala.collection.SortedMap[Long,Class[_ <: Migration]],
   installed_without_available_implementation : scala.collection.SortedSet[Long])
