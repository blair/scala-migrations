package com.imageworks.migration

/**
 * Sealed trait that specifies how the database treats unquoted names
 * and has a method that performs the same conversion.
 */
sealed trait UnquotedNameConverter
{
  /**
   * Apply the same conversion to the unquoted name that the database
   * does.
   *
   * @param name the name to convert
   * @return the converted name
   */
  def apply(name : String) : String
}

/**
 * The database does not modify the case of unquoted names.
 */
case object CasePreservingUnquotedNameConverter
  extends UnquotedNameConverter
{
  def apply(name : String) : String =
  {
    name
  }
}

/**
 * Unquoted names are implicitly converted into their lowercase
 * variant.
 */
case object LowercaseUnquotedNameConverter
  extends UnquotedNameConverter
{
  def apply(name : String) : String =
  {
    name.toLowerCase
  }
}


/**
 * Unquoted names are implicitly converted into their uppercase
 * variant.
 */
case object UppercaseUnquotedNameConverter
  extends UnquotedNameConverter
{
  def apply(name : String) : String =
  {
    name.toUpperCase
  }
}
