/*
 * Copyright (c) 2009 Sony Pictures Imageworks Inc.
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
  def apply(name: String): String
}

/**
 * The database does not modify the case of unquoted names.
 */
case object CasePreservingUnquotedNameConverter
  extends UnquotedNameConverter
{
  def apply(name: String): String =
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
  def apply(name: String): String =
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
  def apply(name: String): String =
  {
    name.toUpperCase
  }
}
