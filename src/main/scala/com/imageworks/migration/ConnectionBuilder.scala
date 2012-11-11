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

import java.sql.{Connection,
                 DriverManager}
import javax.sql.DataSource

/**
 * Adapter class for getting a Connection from either the
 * DriverManager or a DataSource.
 */
class ConnectionBuilder private (either: Either[DataSource,String],
                                 login_opt: Option[(String,String)])
{
  /**
   * Construct a connection builder for a database that does not need
   * a username and password.
   *
   * @param url the JDBC URL to connect to the database
   */
  def this(url: String) =
  {
    this(Right(url), None)
  }

  /**
   * Construct a connection builder for a database that needs a
   * username and password.
   *
   * @param url the JDBC URL to connect to the database
   * @param username the username to log into the database
   * @param password the password associated with the database
   *        username
   */
  def this(url: String,
           username: String,
           password: String) =
  {
    this(Right(url), Some((username, password)))
  }

  /**
   * Construct a connection builder with a DataSource for a database
   * that does not need a username and password.
   *
   * @param datasource the JDBC DataSource to connect to the
   *        database
   */
  def this(datasource: DataSource) =
  {
    this(Left(datasource), None)
  }

  /**
   * Construct a connection builder with a DataSource and override the
   * default username and password.
   *
   * @param datasource the JDBC DataSource to connect to the database
   * @param username the username to log into the database
   * @param password the password associated with the database
   *        username
   */
  def this(datasource: DataSource,
           username: String,
           password: String) =
  {
    this(Left(datasource), Some((username, password)))
  }

  def withConnection[R](commit_behavior: CommitBehavior)
                       (f: Function[Connection,R]): R =
  {
    val connection =
      (either, login_opt) match {
        case (Left(datasource), Some((username, password))) =>
          datasource.getConnection(username, password)
        case (Left(datasource), None) =>
          datasource.getConnection
        case (Right(url), Some((username, password))) =>
          DriverManager.getConnection(url, username, password)
        case (Right(url), None) =>
          DriverManager.getConnection(url)
      }

    With.autoClosingConnection(connection) { c =>
      With.autoCommittingConnection(c, commit_behavior)(f)
    }
  }
}
