/*
 * Copyright (c) 2009 Sony Pictures Imageworks Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the
 * distribution.  Neither the name of Sony Pictures Imageworks nor the
 * names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.imageworks.migration

/**
 * The base trait for all foreign key actions upon delete or update.
 */
sealed trait ForeignKeyConstraintAction
{
  val sql: String
}

/**
 * Delete any rows in the referencing table that refer to rows deleted
 * in the referenced table, or update the row's value to the new value
 * in the referenced row if it was updated.
 */
case object Cascade
  extends ForeignKeyConstraintAction
{
  override
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
  override
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
  override
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
  override
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
  override
  val sql = "SET NULL"
}
