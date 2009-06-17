package com.imageworks.migration

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
  /**
   * Take a SQL result set, pass it to a closure and ensure that the
   * result set is closed after the closure returns, either normally
   * or by an exception.  If the closure returns normally, return its
   * result.
   *
   * @param rs the a SQL result set
   * @param f a Function1[java.sql.ResultSet,R] that operates on the
   *        result set
   * @return the result of f
   */
  def result_set[R](rs : java.sql.ResultSet)
                   (f : java.sql.ResultSet => R) : R =
  {
    try {
      f(rs)
    }
    finally {
      rs.close()
    }
  }
}
