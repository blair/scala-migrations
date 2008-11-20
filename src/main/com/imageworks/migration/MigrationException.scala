package com.imageworks.migration

class MigrationException(message : String)
  extends java.lang.Exception(message)

class DuplicateMigrationDescriptionException(message : String)
  extends MigrationException(message)

class DuplicateMigrationVersionException(message : String)
  extends MigrationException(message)

class IrreversibleMigrationException
  extends MigrationException("This migration is irreversible.")

class MissingMigrationClass(message : String)
  extends MigrationException(message)

class UnsupportedColumnTypeException(message : String)
  extends MigrationException(message)
