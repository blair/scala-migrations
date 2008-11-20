package com.imageworks.migration

/**
 * The base class for all index options.
 */
sealed abstract class IndexOption

case class Name(name : String)
  extends IndexOption
{
  if (name eq null) {
    throw new IllegalArgumentException("The index name cannot be null.")
  }

  if (name.length == 0) {
    throw new IllegalArgumentException("The index name cannot be empty.")
  }
}

case object Unique
  extends IndexOption
