package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import org.jmock.{Expectations,
                  Mockery}

class MigrationVersionAndClassTests
{
  private
  def sign(l : Long) : Int =
  {
    if (l < 0) {
      -1
    }
    else if (l > 0) {
      1
    }
    else {
      0
    }
  }

  @Test
  def can_add_to_java_util_treeset : Unit =
  {
    val s = new java.util.TreeSet[MigrationVersionAndClass]
    val c = classOf[com.imageworks.migration.tests.up_and_down.Migrate_20081118201000_CreateLocationTable]
    val m = new MigrationVersionAndClass(123, c)
    s.add(m)
    s.add(m)
  }

  @Test
  def compareTo : Unit =
  {
    val c1 = classOf[com.imageworks.migration.tests.up_and_down.Migrate_20081118201000_CreateLocationTable]

    for ((v1, v2) <- Array((100L, -100L), (-100L, -100L), (-100L, 100L))) {
      val l1 = v1 : java.lang.Long
      val l2 = v2 : java.lang.Long
      val m1 = new MigrationVersionAndClass(v1, c1)
      val m2 = new MigrationVersionAndClass(v2, c1)
      assertEquals(sign(l1.compareTo(l2)), sign(m1.compareTo(m2)))
      assertEquals(sign(l2.compareTo(l1)), sign(m2.compareTo(m1)))
    }

    // Test that ordering on the same version number works.  There
    // should never be two or more migration classes with the same
    // version number, but just in case there is, the ordering should
    // be consistent.
    val c2 = classOf[com.imageworks.migration.tests.up_and_down.Migrate_20081118201742_CreatePeopleTable]
    val m1 = new MigrationVersionAndClass(100, c1)
    val m2 = new MigrationVersionAndClass(100, c2)
    assertEquals(-1, sign(m1.compareTo(m2)))
    assertEquals(1, sign(m2.compareTo(m1)))
  }
}
