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
