package com.imageworks.migration

object RichConnection
{
  implicit
  def connectionToRichConnection(c : java.sql.Connection) : RichConnection =
  {
    new RichConnection(c)
  }
}
/**
 * A rich java.sql.Connection class that provides a
 * with_prepared_statement method.
 */
class RichConnection(self : java.sql.Connection)
{
  def with_prepared_statement[T](sql : String)
                                (f : java.sql.PreparedStatement => T) : T =
  {
    val statement = self.prepareStatement(sql)
    try {
      f(statement)
    }
    finally {
      statement.close
    }
  }
}
