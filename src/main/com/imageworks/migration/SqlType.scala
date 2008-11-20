package com.imageworks.migration

sealed abstract class SqlType
case object BooleanType extends SqlType
case object CharType extends SqlType
case object IntegerType extends SqlType
case object VarbinaryType extends SqlType
case object VarcharType extends SqlType
