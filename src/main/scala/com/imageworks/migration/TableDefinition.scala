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
 * A builder to define a table.  Its methods add the specified type of
 * column to the table's definition.
 */
class TableDefinition(adapter: DatabaseAdapter,
                      table_name: String)
{
  private
  val columns = new scala.collection.mutable.ListBuffer[ColumnDefinition]

  /**
   * Generate a SQL string representation of the columns in the table.
   *
   * @return the SQL text that defines the columns in the table
   */
  final
  def toSql: String =
  {
    columns.map(_.toSql).mkString("", ", ", "")
  }

  /**
   * Add any known column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param column_type the type of column being added
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def column(name: String,
             column_type: SqlType,
             options: ColumnOption*): TableDefinition =
  {
    val column = adapter.newColumnDefinition(table_name,
                                             name,
                                             column_type,
                                             options: _*)
    columns += column
    this
  }

  /**
   * Add a BIGINT column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def bigint(name: String,
             options: ColumnOption*): TableDefinition =
  {
    column(name, BigintType, options: _*)
  }

  /**
   * Add a BLOB column type to the table.  The actual SQL text used to
   * create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def blob(name: String,
           options: ColumnOption*): TableDefinition =
  {
   column(name, BlobType, options: _*)
  }

  /**
   * Add a BOOLEAN column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def boolean(name: String,
              options: ColumnOption*): TableDefinition =
  {
   column(name, BooleanType, options: _*)
  }

  /**
   * Add a CHAR column type to the table.  The actual SQL text used to
   * create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def char(name: String,
           options: ColumnOption*): TableDefinition =
  {
    column(name, CharType, options: _*)
  }

  /**
   * Add a DECIMAL column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def decimal(name: String,
              options: ColumnOption*): TableDefinition =
  {
    column(name, DecimalType, options: _*)
  }

  /**
   * Add a INTEGER column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def integer(name: String,
              options: ColumnOption*): TableDefinition =
  {
    column(name, IntegerType, options: _*)
  }

  /**
   * Add a SMALLINT column type to the table.  The actual SQL text
   * used to create the column is chosen by the database adapter and
   * may be different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def smallint(name: String,
               options: ColumnOption*): TableDefinition =
  {
    column(name, SmallintType, options: _*)
  }

  /**
   * Add a TIMESTAMP column type to the table.  The actual SQL text
   * used to create the column is chosen by the database adapter and
   * may be different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def timestamp(name: String,
                options: ColumnOption*): TableDefinition =
  {
    column(name, TimestampType, options: _*)
  }

  /**
   * Add a VARBINARY column type to the table.  The actual SQL text
   * used to create the column is chosen by the database adapter and
   * may be different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def varbinary(name: String,
                options: ColumnOption*): TableDefinition =
  {
    column(name, VarbinaryType, options: _*)
  }

  /**
   * Add a VARCHAR column type to the table.  The actual SQL text used
   * to create the column is chosen by the database adapter and may be
   * different than the name of the column_type argument.
   *
   * @param name the column's name
   * @param a possibly empty array of column options to customize the
   *        column
   * @return the same instance
   */
  final
  def varchar(name: String,
              options: ColumnOption*): TableDefinition =
  {
    column(name, VarcharType, options: _*)
  }
}
