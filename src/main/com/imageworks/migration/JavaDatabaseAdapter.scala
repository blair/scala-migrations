package com.imageworks.migration

/**
 * The Scala Database Adapter classes uses Scala case classes in their public
 * constructors and public methods which makes it difficult to use from a pure
 * Java environment.  This class provides Java-friendly factory functions to
 * create adapters.
 */
object JavaDatabaseAdapter
{
  /**
   * Create a Derby Database Adapter.
   *
   * @return newly constructed DerbyDatabaseAdapter
   */
  def getDerbyDatabaseAdapter() : DerbyDatabaseAdapter =
  {
    new DerbyDatabaseAdapter(None)
  }

  /**
   * Create a Derby Database Adapter.
   *
   * @param schema_name the default schema name in the adapter
   * @return newly constructed DerbyDatabaseAdapter
   */
  def getDerbyDatabaseAdapter(schema_name : String) : DerbyDatabaseAdapter =
  {
    new DerbyDatabaseAdapter(Some(schema_name))
  }

  /**
   * Create an Oracle Database Adapter.
   *
   * @return newly constructed OracleDatabaseAdapter
   */
  def getOracleDatabaseAdapter() : OracleDatabaseAdapter =
  {
    new OracleDatabaseAdapter(None)
  }

  /**
   * Create an Oracle Database Adapter.
   *
   * @param schema_name the default schema name in the adapter
   * @return newly constructed OracleDatabaseAdapter
   */
  def getOracleDatabaseAdapter(schema_name : String) : OracleDatabaseAdapter =
  {
    new OracleDatabaseAdapter(Some(schema_name))
  }
}
