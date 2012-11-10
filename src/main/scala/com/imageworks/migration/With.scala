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
import java.util.jar.JarFile

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
   * Given a resource and two functions, the first, a closer function
   * that closes or releases the resource, and the second, a body
   * function that uses the resource, invoke the body function on the
   * resource and then ensure that the closer function closes the
   * resource, regardless if the body function returns normally or
   * throws an exception.
   *
   * @param resource a resource to use and then close
   * @param closerDescription a textual description of what the closer
   *        does; used to log any exception thrown by closer when the
   *        body also throws an exception since in that case the
   *        closer's exception will be suppressed and not thrown to
   *        the caller
   * @param closer the function that closes the resource
   * @param body the function that uses the resource
   * @return the result of invoking body on the resource
   * @throws any exception that invoking body on the resource throws
   */
  def resource[A,B](resource: A, closerDescription: String)
                   (closer: A => Unit)
                   (body: A => B): B =
  {
    var primaryException: Throwable = null
    try {
      body(resource)
    }
    catch {
      case e => {
        primaryException = e
        throw e
      }
    }
    finally {
      if (primaryException eq null) {
        closer(resource)
      }
      else {
        try {
          closer(resource)
        }
        catch {
          case e =>
            logger.warn("Suppressing exception when " +
                        closerDescription +
                        ':',
                        e)
        }
      }
    }
  }

  /**
   * Take a SQL connection, pass it to a closure and ensure that the
   * connection is closed after the closure returns, either normally
   * or by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param connection a SQL connection
   * @param f a Function1[C <: Connection,R] that operates on the
   *        connection
   * @return the result of f
   */
  def autoClosingConnection[C <: Connection,R](connection: C)
                                              (f: C => R): R =
  {
    resource(connection, "closing connection")(_.close())(f)
  }


  /**
   * Take a SQL connection, save its current auto-commit mode, put the
   * connection into the requested auto-commit mode, pass the
   * connection to a closure and ensure that the connection's
   * auto-commit mode is restored after the closure returns, either
   * normally or by an exception.  If the closure returns normally,
   * return its result.
   *
   * The connection's auto-commit mode is always set, even if it is
   * the same as the requested mode.  This is done to ensure any work
   * the database would normally do when setting the auto-commit mode
   * is always done.
   *
   * @param connection a SQL connection
   * @param mode the auto-commit mode the connection's state should be
   *        put in
   * @param f a Function1[C <: Connection,R] that operates on the
   *        connection
   * @return the result of f
   */
  def autoRestoringConnection[C <: Connection,R](connection: C,
                                                 mode: Boolean)
                                                (f: C => R): R =
  {
    val current_mode = connection.getAutoCommit
    With.resource(connection, "restoring connection auto-commit")(_.setAutoCommit(current_mode)) { c =>
      c.setAutoCommit(mode)
      f(c)
    }
  }

  /**
   * Take a SQL connection, pass it to a closure and ensure that any
   * work done on the connection after the closure returns is either
   * left alone, committed or rolled back depending upon the given
   * setting.  If the closure returns normally, return its result.
   * The connection's auto-commit mode will be set and restored.
   *
   * @param connection a SQL connection
   * @param commit_behavior the operation to implement on the
   *        connection after f returns normally or via throwing an
   *        exception
   * @param f a Function1[C <: Connection,R] that operates on the
   *        connection
   * @return the result of f
   */
  def autoCommittingConnection[C <: Connection,R](connection: C,
                                                  commit_behavior: CommitBehavior)
                                                 (f: C=> R): R =
  {
    val new_auto_commit =
      commit_behavior match {
        case AutoCommit => true
        case CommitUponReturnOrException => false
        case CommitUponReturnOrRollbackUponException => false
      }

    With.autoRestoringConnection(connection, new_auto_commit) { c =>
      commit_behavior match {
        case AutoCommit => {
          f(connection)
        }

        case CommitUponReturnOrException => {
          With.resource(connection, "committing transaction")(_.commit())(f)
        }

        case CommitUponReturnOrRollbackUponException => {
          val result =
            try {
              f(connection)
            }
            catch {
              case e1 => {
                try {
                  connection.rollback()
                }
                catch {
                  case e2 =>
                    logger.warn("Suppressing exception when rolling back" +
                                "transaction:", e2)
                }
                throw e1
              }
            }

          connection.commit()

          result
        }
      }
    }
  }

  /**
   * Take a SQL statement, pass it to a closure and ensure that the
   * statement is closed after the closure returns, either normally or
   * by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param statement a SQL statement
   * @param f a Function1[Statement,R] that operates on the statement
   * @return the result of f
   */
  def autoClosingStatement[S <: Statement,R](statement: S)
                                            (f: S => R): R =
  {
    resource(statement, "closing statement")(_.close())(f)
  }

  /**
   * Take a SQL result set, pass it to a closure and ensure that the
   * result set is closed after the closure returns, either normally
   * or by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param resultSet a SQL result set
   * @param f a Function1[ResultSet,R] that operates on the result set
   * @return the result of f
   */
  def autoClosingResultSet[R](resultSet: ResultSet)
                             (f: ResultSet => R): R =
  {
    resource(resultSet, "closing result set")(_.close())(f)
  }

  /**
   * Take a jar file, pass it to a closure and ensure that the jar
   * file is closed after the closure returns, either normally or by
   * an exception.  If the closure returns normally, return its
   * result.
   *
   * @param jarFile a jar file
   * @param f a Function1[J <: JarFile,R] that operates on the jar
   *        file
   * @return the result of f
   */
  def jarFile[J <: JarFile,R](jarFile: J)
                             (f: J => R): R =
  {
    resource(jarFile, "closing jar file")(_.close())(f)
  }
}
