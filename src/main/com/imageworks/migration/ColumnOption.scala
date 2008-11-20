package com.imageworks.migration

/**
 * The base class for all column options.  This is not a sealed class
 * so database specific column options can be defined.
 */
abstract class ColumnOption

case class Default(value : String)
  extends ColumnOption

case class Limit(length : Int)
  extends ColumnOption
{
  if (length < 0) {
    val message = "The limit in " +
                  this +
                  " must be greater than or equal to one."
    throw new IllegalArgumentException(message)
  }
}

case object NotNull
  extends ColumnOption

case object Nullable
  extends ColumnOption
