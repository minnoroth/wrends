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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.types.RawModification;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the
 * {@link org.opends.server.util.ModifyChangeRecordEntry} class.
 * <p>
 * Note that we test shared behaviour with the abstract
 * {@link org.opends.server.util.ChangeRecordEntry} class in case it has
 * been overridden.
 */
public final class TestModifyChangeRecordEntry extends UtilTestCase {
  /** Set of changes. */
  private List<RawModification> modifications;

  /** The attribute being added in the modifications. */
  private Attribute attribute;

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

    // Create a simple set of modifications.
    modifications = new ArrayList<>();
    attribute = Attributes.create("cn", "hello world");
    LDAPAttribute lattribute = new LDAPAttribute(attribute);
    LDAPModification modification = new LDAPModification(
        ModificationType.ADD, lattribute);
    modifications.add(modification);
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
    new ModifyChangeRecordEntry(null, modifications);
  }

  /**
   * Tests the constructor with empty DN.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testConstructorEmptyDN() throws Exception {
    ModifyChangeRecordEntry entry = new ModifyChangeRecordEntry(DN.rootDN(),
        modifications);

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

    ModifyChangeRecordEntry entry = new ModifyChangeRecordEntry(testDN1,
        modifications);

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
    ModifyChangeRecordEntry entry = new ModifyChangeRecordEntry(DN.rootDN(),
        modifications);

    Assert.assertEquals(entry.getChangeOperationType(),
        ChangeOperationType.MODIFY);
  }

  /**
   * Tests getModifications method for empty modifications.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetModificationsEmpty() throws Exception {
    List<RawModification> empty = Collections.emptyList();
    ModifyChangeRecordEntry entry = new ModifyChangeRecordEntry(DN.rootDN(),
                                                                empty);

    List<RawModification> mods = entry.getModifications();
    Assert.assertEquals(mods.size(), 0);
  }

  /**
   * Tests getModifications method for non-empty modifications.
   *
   * @throws Exception
   *           If the test failed unexpectedly.
   */
  @Test
  public void testGetModificationsNonEmpty() throws Exception {
    ModifyChangeRecordEntry entry = new ModifyChangeRecordEntry(DN.rootDN(),
        modifications);

    List<RawModification> mods = entry.getModifications();
    Assert.assertEquals(mods.size(), 1);

    RawModification first = mods.get(0);
    Assert.assertEquals(first.getModificationType(), ModificationType.ADD);
    TestCaseUtils.assertObjectEquals(first.getAttribute().toAttribute(), attribute);
  }
}
