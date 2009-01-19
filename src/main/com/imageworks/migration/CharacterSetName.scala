package com.imageworks.migration

/**
 * The base trait for all character set names.
 */
sealed trait CharacterSetName

/**
 * The database column should be encoded using Unicode.
 */
case object Unicode
  extends CharacterSetName
