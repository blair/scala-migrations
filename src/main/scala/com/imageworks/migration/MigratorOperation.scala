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
case class MigrateToVersion(version: Long)
  extends MigratorOperation

/**
 * Rollback 'count' migrations in the database.  This is different
 * than using MigrateToVersion to migrate to the same version, as
 * MigrateToVersion will also install any missing migration with a
 * version less then the target version.  This rollback operation only
 * removes migrations from the database.
 */
case class RollbackMigration(count: Int)
  extends MigratorOperation
{
  if (count < 1) {
    val message = "The number of migrations to rollback must be greater " +
                  "than zero."
    throw new IllegalArgumentException(message)
  }
}
