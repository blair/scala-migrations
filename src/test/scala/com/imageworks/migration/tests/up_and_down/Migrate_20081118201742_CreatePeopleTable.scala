/*
 * Copyright (c) 2010 Sony Pictures Imageworks Inc.
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
package com.imageworks.migration.tests.up_and_down

import com.imageworks.migration.{Cascade,
                                 CharacterSet,
                                 Check,
                                 Default,
                                 Limit,
                                 Migration,
                                 Name,
                                 NotNull,
                                 Nullable,
                                 OnDelete,
                                 OnUpdate,
                                 Precision,
                                 PrimaryKey,
                                 Restrict,
                                 Scale,
                                 Unicode,
                                 Unique,
                                 VarbinaryType}

class Migrate_20081118201742_CreatePeopleTable
  extends Migration
{
  def up(): Unit =
  {
    createTable("people") { t =>
      t.varbinary("pk_people", PrimaryKey, Limit(16))
      t.varbinary("pk_location", Limit(16), NotNull)
      t.integer("employee_id", Unique)
      t.integer("ssn", NotNull)
      t.varchar("first_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.char("middle_initial", Limit(1), Nullable)
      t.varchar("last_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.timestamp("birthdate", Limit(0), NotNull)
      t.smallint("height", NotNull, Check("height > 0"))
      t.smallint("weight", NotNull, Check("weight > 0"))
      t.integer("vacation_days", NotNull, Default("0"))
      t.bigint("hire_time_micros", NotNull)
      t.decimal("salary", Precision(7), Scale(2), Check("salary > 0"))
      t.blob("image")
    }

    addIndex("people", "ssn", Unique)

    addForeignKey(on("people" -> "pk_location"),
                  references("location" -> "pk_location"),
                  OnDelete(Cascade),
                  OnUpdate(Restrict))

    if (! addingForeignKeyConstraintCreatesIndex) {
      addIndex("people", "pk_location", Name("idx_people_pk_location"))
    }

    addColumn("people", "secret_key", VarbinaryType, Limit(16))

    addCheck(on("people" -> "vacation_days"), "vacation_days >= 0")
  }

  def down(): Unit =
  {
    removeCheck(on("people" -> "vacation_days"))
    if (! addingForeignKeyConstraintCreatesIndex) {
      removeIndex("people", "pk_location", Name("idx_people_pk_location"))
    }
    removeForeignKey(on("people" -> "pk_location"),
                     references("location" -> "pk_location"))

    removeIndex("people", "ssn")
    removeColumn("people", "secret_key")
    dropTable("people")
  }
}
