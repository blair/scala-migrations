/*
 * Copyright (c) 2009 Sony Pictures Imageworks
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

class TableDefinition(adapter : DatabaseAdapter,
                      table_name : String)
{
  private
  val columns = new scala.collection.mutable.ListBuffer[ColumnDefinition]

  final
  def to_sql : String =
  {
    columns.map(_.to_sql).mkString("", ", ", "")
  }

  final
  def column(name : String,
             column_type : SqlType,
             options : ColumnOption*) : TableDefinition =
  {
    val column = adapter.new_column_definition(table_name,
                                               name,
                                               column_type,
                                               options : _*)
    columns += column
    this
  }

  final
  def bigint(name : String,
             options : ColumnOption*) : TableDefinition =
  {
    column(name, BigintType, options : _*)
  }

  final
  def blob(name : String,
           options : ColumnOption*) : TableDefinition =
  {
   column(name, BlobType, options : _*)
  }

  final
  def boolean(name : String,
              options : ColumnOption*) : TableDefinition =
  {
   column(name, BooleanType, options : _*)
  }

  final
  def char(name : String,
           options : ColumnOption*) : TableDefinition =
  {
    column(name, CharType, options : _*)
  }

  final
  def decimal(name : String,
              options : ColumnOption*) : TableDefinition =
  {
    column(name, DecimalType, options : _*)
  }

  final
  def integer(name : String,
              options : ColumnOption*) : TableDefinition =
  {
    column(name, IntegerType, options : _*)
  }

  final
  def smallint(name : String,
               options : ColumnOption*) : TableDefinition =
  {
    column(name, SmallintType, options : _*)
  }

  final
  def timestamp(name : String,
                options : ColumnOption*) : TableDefinition =
  {
    column(name, TimestampType, options : _*)
  }

  final
  def varbinary(name : String,
                options : ColumnOption*) : TableDefinition =
  {
    column(name, VarbinaryType, options : _*)
  }

  final
  def varchar(name : String,
              options : ColumnOption*) : TableDefinition =
  {
    column(name, VarcharType, options : _*)
  }
}
