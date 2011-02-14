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
package com.imageworks.migration

import org.slf4j.LoggerFactory

import java.sql.{Connection,
                 ResultSet,
                 Statement}

/**
 * Utility object that contains functions that ensure a resource is
 * released once it has been used.  Each function takes resource
 * object that has a method to release the resource, such as close(),
 * and a closure to that operates on the resource.  After the closure
 * has completed, either normally via a return or by throwing an
 * exception, the resource is released.
 */
object With
{
  private final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Take a SQL connection, pass it to a closure and ensure that the
   * connection is closed after the closure returns, either normally
   * or by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param c a SQL connection
   * @param f a Function1[Connection,R] that operates on the
   *        connection
   * @return the result of f
   */
  def connection[R](c: Connection)
                   (f: Connection => R): R =
  {
    try {
      f(c)
    }
    finally {
      try {
        c.close()
      }
      catch {
        case e => logger.warn("Error in closing connection:", e)
      }
    }
  }

  /**
   * Take a SQL statement, pass it to a closure and ensure that the
   * statement is closed after the closure returns, either normally or
   * by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param s a SQL statement
   * @param f a Function1[Statement,R] that operates on the statement
   * @return the result of f
   */
  def statement[S <: Statement,R](s: S)
                                 (f: S => R): R =
  {
    try {
      f(s)
    }
    finally {
      try {
        s.close()
      }
      catch {
        case e => logger.warn("Error in closing statement:", e)
      }
    }
  }

  /**
   * Take a SQL result set, pass it to a closure and ensure that the
   * result set is closed after the closure returns, either normally
   * or by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param rs a SQL result set
   * @param f a Function1[ResultSet,R] that operates on the result set
   * @return the result of f
   */
  def resultSet[R](rs: ResultSet)
                  (f: ResultSet => R): R =
  {
    try {
      f(rs)
    }
    finally {
      try {
        rs.close()
      }
      catch {
        case e => logger.warn("Error in closing result set:", e)
      }
    }
  }
}
