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
package com.imageworks.migration.tests.grant_and_revoke

import com.imageworks.migration.{Limit,
                                 Migration,
                                 NotNull,
                                 PrimaryKey,
                                 Unique}

class Migrate_200811241940_CreateUser
  extends Migration
{
  def up(): Unit =
  {
    // These commands configure Derby to turn on user authentication,
    // create users, and enable SQL authorization, needed for GRANT
    // statements to work.
    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.connection.requireAuthentication', 'true')""")

    // this cannot be undone
    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.database.sqlAuthorization', 'true')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.authentication.provider', 'BUILTIN')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.APP', 'password')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.test', 'password')""")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.database.fullAccessUsers', 'APP')""")

    createTable("location") { t =>
      t.varbinary("pk_location", PrimaryKey, Limit(16))
      t.varchar("name", Unique, Limit(255), NotNull)
    }
  }

  def down(): Unit =
  {
    dropTable("location")

    execute("""CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
      'derby.user.test', null)""")
  }
}
