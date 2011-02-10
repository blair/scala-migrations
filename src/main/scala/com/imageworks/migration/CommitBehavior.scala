/*
 * Copyright (c) 2011 Sony Pictures Imageworks Inc.
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
 * This sealed trait's specifies the commit behavior on a database
 * connection and its subobjects are used as arguments to the
 * Migrator#with*Connection() methods.  The subobjects specify if a
 * new connection's auto-commit mode should be left on or disabled.
 * For connections with auto-commit mode disabled it specifies if the
 * current transaction should be rolled back or committed if the
 * closure passed to Migrator#with*Connection() throws an exception.
 */
private sealed trait CommitBehavior

/**
 * The new database connection's auto-commit mode is left on.  Because
 * the connection is in auto-commit mode the
 * Migrator#with*Connection() methods do not commit nor roll back the
 * transaction any before returning the result of the
 * Migrator#with*Connection()'s closure or rethrowing its exception.
 */
private case object AutoCommit
  extends CommitBehavior

/**
 * The new database connection's auto-commit mode is turned off.
 * Regardless if the closure passed to Migrator#with*Connection()
 * returns or throws an exception the transaction is committed.
 */
private case object CommitUponReturnOrException
  extends CommitBehavior

/**
 * The new database connection's auto-commit mode is turned off.  If
 * the closure passed to the Migrator#with*Connection() returns
 * normally then transaction is committed; if it throws an exception
 * then the transaction is rolled back.
 */
private case object CommitUponReturnOrRollbackUponException
  extends CommitBehavior
