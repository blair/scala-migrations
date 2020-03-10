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

import com.imageworks.migration.{
  Cascade,
  CharacterSet,
  Check,
  Default,
  Limit,
  Migration,
  Name,
  NamedCheck,
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
  VarbinaryType
}

class Migrate_20081118201742_CreatePeopleTable
    extends Migration {
  val tableName = "scala_migrations_people"

  def up() {
    createTable(tableName) { t =>
      t.varbinary("pk_" + tableName, PrimaryKey, Limit(16))
      t.varbinary("pk_scala_migrations_location", Limit(16), NotNull)
      t.integer("employee_id", Unique)
      t.integer("ssn", NotNull)
      t.varchar("first_name", Limit(255), NotNull,
        CharacterSet(Unicode, "utf8_unicode_ci"))
      t.char("middle_initial", Limit(1), Nullable)
      t.varchar("last_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.timestamp("birthdate", Limit(0), NotNull)
      t.smallint("height", NotNull, NamedCheck(
        "chk_height_nonnegative",
        "height > 0"))
      t.smallint("weight", NotNull, Check("weight > 0"))
      t.integer("vacation_days", NotNull, Default("0"))
      t.bigint("hire_time_micros", NotNull)
      t.decimal("salary", Precision(7), Scale(2), Check("salary > 0"))
      t.blob("image")
    }

    addIndex(tableName, "ssn", Unique)

    addForeignKey(
      on(tableName ->
        "pk_scala_migrations_location"),
      references("scala_migrations_location" ->
        "pk_scala_migrations_location"),
      OnDelete(Cascade),
      OnUpdate(Restrict),
      Name("fk_smp_pk_sml_sml_pk_sml"))

    if (!addingForeignKeyConstraintCreatesIndex) {
      addIndex(
        tableName,
        "pk_scala_migrations_location",
        Name("idx_smp_pk_sml"))
    }

    addColumn(tableName, "secret_key", VarbinaryType, Limit(16))

    addCheck(on(tableName -> "vacation_days"), "vacation_days >= 0")
  }

  def down() {
    removeCheck(on(tableName -> "vacation_days"))
    removeForeignKey(
      on(tableName ->
        "pk_scala_migrations_location"),
      references("scala_migrations_location" ->
        "pk_scala_migrations_location"),
      Name("fk_smp_pk_sml_sml_pk_sml"))
    if (!addingForeignKeyConstraintCreatesIndex) {
      removeIndex(
        tableName,
        "pk_scala_migrations_location",
        Name("idx_smp_pk_sml"))
    }

    removeIndex(tableName, "ssn")
    removeColumn(tableName, "secret_key")
    dropTable(tableName)
  }
}
