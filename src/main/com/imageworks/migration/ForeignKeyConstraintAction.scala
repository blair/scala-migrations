package com.imageworks.migration

/**
 * The base trait for all foreign key actions upon delete or update.
 */
sealed trait ForeignKeyConstraintAction
{
  def sql : String
}

/**
 * Delete any rows in the referencing table that refer to rows deleted
 * in the referenced table, or update the row's value to the new value
 * in the referenced row if it was updated.
 */
case object Cascade
  extends ForeignKeyConstraintAction
{
  val sql = "CASCADE"
}

/**
 * Generate an error that updating or deleting the row would cause a
 * foreign key constraint violation.  In some databases, NO ACTION
 * implies a deferred check, after all deletes have been performed.
 */
case object NoAction
  extends ForeignKeyConstraintAction
{
  val sql = "NO ACTION"
}

/**
 * Generate an error that updating or deleting the row would cause a
 * foreign key constraint violation.  This is the same as NoAction,
 * except that any checks are not deferred.
 */
case object Restrict
  extends ForeignKeyConstraintAction
{
  val sql = "RESTRICT"
}

/**
 * Set any rows in the referencing table to their default value when
 * the referenced rows are deleted or updated.  Not all databases
 * support SET DEFAULT for ON UPDATE.
 */
case object SetDefault
  extends ForeignKeyConstraintAction
{
  val sql = "SET DEFAULT"
}

/**
 * Set any rows in the referencing table to NULL when the referenced
 * rows are deleted or updated.  Not all databases support SET DEFAULT
 * for ON UPDATE.
 */
case object SetNull
  extends ForeignKeyConstraintAction
{
  val sql = "SET NULL"
}
