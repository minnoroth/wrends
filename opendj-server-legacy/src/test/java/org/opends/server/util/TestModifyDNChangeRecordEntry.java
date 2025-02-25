/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.ModifyDNChangeRecordEntry} class.
 * <p>
 * Note that we test shared behaviour with the abstract
 * {@link org.opends.server.util.ChangeRecordEntry} class in case it has
 * been overridden.
 */
public final class TestModifyDNChangeRecordEntry extends UtilTestCase {
  private DN newSuperiorDN;

  private RDN newRDN;

  /**
   * Once-only initialization.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so we'll
    // start the server.
    TestCaseUtils.startServer();

    newSuperiorDN = DN.valueOf("dc=com");
    newRDN = RDN.valueOf("dc=foo");
  }

  /**
   * Tests the constructor with null DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test(expectedExceptions = { NullPointerException.class,
                               AssertionError.class })
  public void testConstructorNullDN() throws Exception {
    new ModifyDNChangeRecordEntry(null, newRDN, false, newSuperiorDN);
  }

  /**
   * Tests the constructor with empty DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorEmptyDN() throws Exception {
    ModifyDNChangeRecordEntry entry = new ModifyDNChangeRecordEntry(
        DN.rootDN(), newRDN, false, newSuperiorDN);

    TestCaseUtils.assertObjectEquals(entry.getDN(), DN.rootDN());
  }

  /**
   * Tests the constructor with non-null DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorNonNullDN() throws Exception {
    DN testDN1 = DN.valueOf("dc=hello, dc=world");
    DN testDN2 = DN.valueOf("dc=hello, dc=world");

    ModifyDNChangeRecordEntry entry = new ModifyDNChangeRecordEntry(
        testDN1, newRDN, false, newSuperiorDN);

    TestCaseUtils.assertObjectEquals(entry.getDN(), testDN2);
  }

  /**
   * Tests the change operation type is correct.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testChangeOperationType() throws Exception {
    ModifyDNChangeRecordEntry entry =
         new ModifyDNChangeRecordEntry(DN.rootDN(), newRDN, false, newSuperiorDN);

    Assert.assertEquals(entry.getChangeOperationType(),
        ChangeOperationType.MODIFY_DN);
  }

  /**
   * Tests getNewRDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNewRDN() throws Exception {
    ModifyDNChangeRecordEntry entry =
         new ModifyDNChangeRecordEntry(DN.rootDN(), newRDN, false, newSuperiorDN);

    TestCaseUtils.assertObjectEquals(entry.getNewRDN(), newRDN);
  }

  /**
   * Tests getNewSuperiorDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetNewSuperiorDN() throws Exception {
    ModifyDNChangeRecordEntry entry =
         new ModifyDNChangeRecordEntry(DN.rootDN(), newRDN, false, newSuperiorDN);

    TestCaseUtils.assertObjectEquals(entry.getNewSuperiorDN(), newSuperiorDN);
  }

  /**
   * Tests deleteOldRDN method when false.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDeleteOldRDNFalse() throws Exception {
    ModifyDNChangeRecordEntry entry =
         new ModifyDNChangeRecordEntry(DN.rootDN(), newRDN, false, newSuperiorDN);

    Assert.assertEquals(entry.deleteOldRDN(), false);
  }

  /**
   * Tests deleteOldRDN method.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testDeleteOldRDNTrue() throws Exception {
    ModifyDNChangeRecordEntry entry =
         new ModifyDNChangeRecordEntry(DN.rootDN(), newRDN, true, newSuperiorDN);

    Assert.assertEquals(entry.deleteOldRDN(), true);
  }
}
