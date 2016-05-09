package com.imageworks.migration

class MariadbDatabaseAdapter(override val schemaNameOpt: Option[String])
  extends MysqlDatabaseAdapter(schemaNameOpt)
