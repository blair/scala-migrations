package com.imageworks.migration

sealed abstract class SqlType
case object BigintType extends SqlType
case object BlobType extends SqlType
case object BooleanType extends SqlType
case object CharType extends SqlType
case object DecimalType extends SqlType
case object IntegerType extends SqlType
case object SmallintType extends SqlType
case object TimestampType extends SqlType
case object VarbinaryType extends SqlType
case object VarcharType extends SqlType
