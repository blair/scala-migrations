/*
 * Copyright (c) 2012 Sony Pictures Imageworks Inc.
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
 * Representation of a SQL user.  It provides a single #quoted()
 * method that returns the user properly quoted for inclusion in a SQL
 * statement.
 */
sealed abstract class User {
  /**
   * The user quoted for a SQL statement.
   *
   * @param unquotedNameConverter the unquoted name converter for the
   *        database
   * @return the user quoted for a SQL statement
   */
  def quoted(unquotedNameConverter: UnquotedNameConverter): String
}

/**
 * A user consisting only of a user name.  Uses double quotation marks
 * to quote the user name.
 *
 * @param userName the user name
 */
class PlainUser(userName: String)
    extends User {
  override def quoted(unquotedNameConverter: UnquotedNameConverter): String = {
    '"' + unquotedNameConverter(userName) + '"'
  }
}

/**
 * Object to create PlainUser instances with a user name with a nice
 * syntax, e.g. User('foobar').
 */
object User {
  /**
   * Given a user name, return a PlainUser instance.
   *
   * @param userName a user name
   * @return a PlainUser with the given name
   */
  def apply(userName: String): PlainUser = new PlainUser(userName)
}

object MysqlUser {
  /**
   * Given a user name and a host name return a User appropriate for a
   * MySQL database, see
   * http://dev.mysql.com/doc/refman/5.5/en/account-names.html .
   *
   * @param userName a user name
   * @param hostName a host name
   */
  def apply(
    userName: String,
    hostName: String): MysqlUser = {
    new MysqlUser(userName, hostName)
  }
}

/**
 * Representation of a SQL user for MySQL which consists of a user
 * name and a host name; see
 * http://dev.mysql.com/doc/refman/5.5/en/account-names.html .
 *
 * @param userName the user name
 * @param hostName the host name
 */
class MysqlUser(
  userName: String,
  hostName: String)
    extends User {
  override def quoted(unquotedNameConverter: UnquotedNameConverter): String = {
    val sb = new java.lang.StringBuilder(64)
    sb.append('\'')
      .append(unquotedNameConverter(userName))
      .append("'@'")
      .append(unquotedNameConverter(hostName))
      .append('\'')
      .toString
  }
}

/**
 * A factory for User instances that are built from a user name.
 */
abstract class UserFactory[T <: User] {
  /**
   * Given a user name, return a User instance.
   *
   * @param userName a user name
   * @return a User with the given name
   */
  def nameToUser(userName: String): T
}

/**
 * Singleton UserFactory that creates PlainUser instances.
 */
object PlainUserFactory
    extends UserFactory[PlainUser] {
  override def nameToUser(userName: String) = User(userName)
}

/**
 * A singleton user factory to create MysqlUser instances.  This
 * factory uses the input user name and defaults the host name to
 * "localhost".
 */
object MysqlUserFactory
    extends UserFactory[MysqlUser] {
  override def nameToUser(userName: String): MysqlUser = {
    new MysqlUser(userName, "localhost")
  }
}
