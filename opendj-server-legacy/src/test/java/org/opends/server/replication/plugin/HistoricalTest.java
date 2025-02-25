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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.service.ReplicationBroker;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.util.TestTimer;
import org.opends.server.util.TestTimer.CallableVoid;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tests the Historical class. */
@SuppressWarnings("javadoc")
public class HistoricalTest extends ReplicationTestCase
{

  private int replServerPort;
  private String testName = "historicalTest";

  /**
   * Set up replication on the test backend.
   */
  @BeforeClass
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    // Create an internal connection.
    connection = InternalClientConnection.getRootConnection();

    replServerPort = TestCaseUtils.findFreePort();

    // The replication server.
    String replServerStringDN = "cn=Replication Server, " + SYNCHRO_PLUGIN_DN;
    String replServerLdif = "dn: " + replServerStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-replication-server\n"
         + "cn: replication Server\n"
         + "ds-cfg-replication-port: " + replServerPort + "\n"
         + "ds-cfg-replication-db-directory: HistoricalTest\n"
         + "ds-cfg-replication-server-id: 102\n";

    // The suffix to be synchronized.
    String synchroServerStringDN = "cn=" + testName + ", cn=domains, " +
      SYNCHRO_PLUGIN_DN;
    String synchroServerLdif = "dn: " + synchroServerStringDN + "\n"
         + "objectClass: top\n"
         + "objectClass: ds-cfg-replication-domain\n"
         + "cn: " + testName + "\n"
         + "ds-cfg-base-dn: " + TEST_ROOT_DN_STRING + "\n"
         + "ds-cfg-replication-server: localhost:" + replServerPort + "\n"
         + "ds-cfg-server-id: 1\n"
         + "ds-cfg-receive-status: true\n";

    configureReplication(replServerLdif, synchroServerLdif);
  }

  /**
   * Tests that the attribute modification history is correctly read from
   * and written to an operational attribute of the entry.
   * Also test that historical is purged according to the purge delay that
   * is provided.
   */
  @Test(enabled=true)
  public void testEncodingAndPurge() throws Exception
  {
    //  Add a test entry.
    TestCaseUtils.addEntry(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: user.1",
        "cn: Aaccf Amar",
        "sn: Amar",
        "givenName: Aaccf",
        "userPassword: password",
        "description: Initial description",
        "displayName: 1"
    );

    // Modify the test entry to give it some history.
    // Test both single and multi-valued attributes.

    String path = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "add: cn;lang-en",
        "cn;lang-en: Aaccf Amar",
        "cn;lang-en: Aaccf A Amar",
        "-",
        "replace: description",
        "description: replaced description",
        "-",
        "add: displayName",
        "displayName: 2",
        "-",
        "delete: displayName",
        "displayName: 1",
        "-"
    );

    String[] args =
    {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
    };

    ldapmodify(args);

    args[9] = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "replace: displayName",
        "displayName: 2",
        "-"
    );

    ldapmodify(args);

    // Read the entry back to get its history operational attribute.
    DN dn = DN.valueOf("uid=user.1," + TEST_ROOT_DN_STRING);
    Entry entry = DirectoryServer.getEntry(dn);

    Iterable<Attribute> attrs = EntryHistorical.getHistoricalAttr(entry);
    Attribute before = attrs.iterator().next();

    // Check that encoding and decoding preserves the history information.
    EntryHistorical hist = EntryHistorical.newInstanceFromEntry(entry);
    assertEquals(hist.getLastPurgedValuesCount(),0);
    TestCaseUtils.assertObjectEquals(hist.encodeAndPurge(), before);

    Thread.sleep(1000);

    args[9] = TestCaseUtils.createTempFile(
        "dn: uid=user.1," + TEST_ROOT_DN_STRING,
        "changetype: modify",
        "replace: displayName",
        "displayName: 3",
        "-"
    );
    ldapmodify(args);

    long testPurgeDelayInMillisec = 1000; // 1 sec

    // Read the entry back to get its history operational attribute.
    entry = DirectoryServer.getEntry(dn);
    hist = EntryHistorical.newInstanceFromEntry(entry);
    hist.setPurgeDelay(testPurgeDelayInMillisec);

    // The purge time is not done so the hist attribute should be not empty
    assertFalse(hist.encodeAndPurge().isEmpty());

    // Now wait for the purge time to be done
    Thread.sleep(testPurgeDelayInMillisec + 200);

    // Read the entry back to get its history operational attribute.
    // The hist attribute should now be empty since purged
    entry = DirectoryServer.getEntry(dn);
    hist = EntryHistorical.newInstanceFromEntry(entry);
    hist.setPurgeDelay(testPurgeDelayInMillisec);
    assertTrue(hist.encodeAndPurge().isEmpty());
    assertEquals(hist.getLastPurgedValuesCount(),11);
  }

  /**
   * The scenario for this test case is that two modify operations occur at
   * two different servers at nearly the same time, each operation adding a
   * different value for a single-valued attribute.  Replication then
   * replays the operations and we expect the conflict to be resolved on both
   * servers by keeping whichever value was actually added first.
   * For the unit test, we employ a single directory server.  We use the
   * broker API to simulate the ordering that would happen on the first server
   * on one entry, and the reverse ordering that would happen on the
   * second server on a different entry.  Confused yet?
   */
  @Test(enabled=true, groups="slow")
  public void conflictSingleValue() throws Exception
  {
    final DN dn1 = DN.valueOf("cn=test1," + TEST_ROOT_DN_STRING);
    final DN dn2 = DN.valueOf("cn=test2," + TEST_ROOT_DN_STRING);
    final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    final AttributeType attrType = getServerContext().getSchema().getAttributeType("displayname");
    final AttributeDescription attrDesc = AttributeDescription.create(attrType);
    final AttributeDescription entryuuidDesc = AttributeDescription.create(getEntryUUIDAttributeType());

    /*
     * Open a session to the replicationServer using the broker API.
     * This must use a different serverId to that of the directory server.
     */
    ReplicationBroker broker =
      openReplicationSession(baseDN, 2, 100, replServerPort, 1000);


    // Clear the backend and create top entry
    TestCaseUtils.initializeTestBackend(true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test1," + TEST_ROOT_DN_STRING,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test1",
         "sn: test"
       );

    // Read the entry back to get its UUID.
    String entryuuid = getEntryValue(dn1, entryuuidDesc);

    // Add the second test entry.
    TestCaseUtils.addEntry(
         "dn: cn=test2," + TEST_ROOT_DN_STRING,
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "cn: test2",
         "sn: test",
         "description: Description"
       );

    // Read the entry back to get its UUID.
    String entryuuid2 = getEntryValue(dn2, entryuuidDesc);

    long now = System.currentTimeMillis();
    final int serverId1 = 3;
    final int serverId2 = 4;
    CSN t1 = new CSN(now,    0,  serverId1);
    CSN t2 = new CSN(now+1,  0,  serverId2);

    // Simulate the ordering t1:add:A followed by t2:add:B that would
    // happen on one server.

    // Replay an add of a value A at time t1 on a first server.
    publishModify(broker, t1, dn1, entryuuid, attrType, "A");
    waitUntilEntryValueEquals(dn1, attrDesc, "A");

    // Replay an add of a value B at time t2 on a second server.
    publishModify(broker, t2, dn1, entryuuid, attrType, "B");

    // Simulate the reverse ordering t2:add:B followed by t1:add:A that
    // would happen on the other server.

    t1 = new CSN(now+3,  0,  serverId1);
    t2 = new CSN(now+4,  0,  serverId2);

    publishModify(broker, t2, dn2, entryuuid2, attrType, "B");
    waitUntilEntryValueEquals(dn2, attrDesc, "B");

    // Replay an add of a value A at time t1 on a first server.
    publishModify(broker, t1, dn2, entryuuid2, attrType, "A");

    // See how the conflicts were resolved.
    // The two values should be the first value added.
    waitUntilEntryValueEquals(dn1, attrDesc, "A");
    waitUntilEntryValueEquals(dn2, attrDesc, "A");

    TestCaseUtils.deleteEntry(dn1);
    TestCaseUtils.deleteEntry(dn2);
  }

  private void waitUntilEntryValueEquals(final DN entryDN, final AttributeDescription attrDesc,
      final String expectedValue) throws Exception
  {
    final TestTimer timer = new TestTimer.Builder()
      .maxSleep(2, SECONDS)
      .sleepTimes(100, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new CallableVoid()
    {
      @Override
      public void call() throws Exception
      {
        assertEquals(getEntryValue(entryDN, attrDesc), expectedValue);
      }
    });
  }

  private String getEntryValue(final DN dn, AttributeDescription attrDesc) throws DirectoryException
  {
    Entry entry = DirectoryServer.getEntry(dn);
    Attribute attr = entry.getAttribute(attrDesc);
    Assertions.assertThat(attr).hasSize(1);
    return attr.iterator().next().toString();
  }

  private static void publishModify(ReplicationBroker broker, CSN changeNum,
      DN dn, String entryuuid, AttributeType attrType, String newValue)
  {
    Attribute attr = Attributes.create(attrType, newValue);
    List<Modification> mods = newArrayList(new Modification(ModificationType.ADD, attr));
    broker.publish(new ModifyMsg(changeNum, dn, mods, entryuuid));
  }

  /**
   * Test that historical information is correctly added when performing ADD,
   * MOD and MODDN operations.
   */
  @Test
  public void historicalAdd() throws Exception
  {
    final DN dn1 = DN.valueOf("cn=testHistoricalAdd,o=test");

    // Clear the backend.
    TestCaseUtils.initializeTestBackend(true);

    // Add the first test entry.
    TestCaseUtils.addEntry(
        "dn: " + dn1,
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1",
        "sn: test"
    );

    // Read the entry that was just added.
    Entry entry = DirectoryServer.getEntry(dn1);

    // Check that we can build an Add operation from this entry.
    // This will ensure both that the Add historical information is
    // correctly added and also that the code that rebuild operation
    // from this historical information is working.
    Iterable<FakeOperation> ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the Operation to see that it
    // was correctly generated.
    assertFakeOperations(dn1, entry, ops, 1);

    // Now apply a modifications to the entry and check that the
    // ADD historical information has been preserved.
    TestCaseUtils.applyModifications(false,
        "dn: " + dn1,
        "changetype: modify",
        "add: description",
    "description: foo");

    // Read the modified entry.
    entry = DirectoryServer.getEntry(dn1);

    // use historical information to generate new list of operations
    // equivalent to the operations that have been applied to this entry.
    ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the operation list to see that it
    // was correctly generated.
    assertFakeOperations(dn1, entry, ops, 2);

    // rename the entry.
    TestCaseUtils.applyModifications(false,
        "dn: " + dn1,
        "changetype: moddn",
        "newrdn: cn=test2",
    "deleteoldrdn: 1");

    // Read the modified entry.
    final DN dn2 = DN.valueOf("cn=test2,o=test");
    entry = DirectoryServer.getEntry(dn2);

    // use historical information to generate new list of operations
    // equivalent to the operations that have been applied to this entry.
    ops = EntryHistorical.generateFakeOperations(entry);

    // Perform a few check on the operation list to see that it
    // was correctly generated.
    assertFakeOperations(dn2, entry, ops, 3);

    // Now clear the backend and try to run the generated operations
    // to check that applying them do lead to an equivalent result.
    TestCaseUtils.initializeTestBackend(true);

    for (FakeOperation fake : ops)
    {
      LDAPUpdateMsg msg = (LDAPUpdateMsg) fake.generateMessage();
      Operation op =
        msg.createOperation(InternalClientConnection.getRootConnection());
      op.setInternalOperation(true);
      op.setSynchronizationOperation(true);
      op.run();
    }

    Entry newEntry = DirectoryServer.getEntry(dn2);
    TestCaseUtils.assertObjectEquals(entry.getName(), newEntry.getName());
  }

  /**
   * Performs a few check on the provided ADD operations, particularly
   * that a ADDmsg can be created from it with valid values for fields
   * DN, entryuid, ...)
   */
  private void assertFakeOperations(final DN dn1, Entry entry,
      Iterable<FakeOperation> ops, int assertCount) throws Exception
  {
    int count = 0;
    for (FakeOperation op : ops)
    {
      count++;
      if (op instanceof FakeAddOperation)
      {
        // perform a few check on the Operation to see that it
        // was correctly generated :
        // - the dn should be dn1,
        // - the entry id and the parent id should match the ids from the entry
        FakeAddOperation addOp = (FakeAddOperation) op;
        assertNotNull(addOp.getCSN());
        AddMsg addmsg = addOp.generateMessage();
        TestCaseUtils.assertObjectEquals(dn1, addmsg.getDN());
        assertEquals(addmsg.getEntryUUID(), EntryHistorical.getEntryUUID(entry));
        String parentId = LDAPReplicationDomain.findEntryUUID(dn1.parent());
        assertEquals(addmsg.getParentEntryUUID(), parentId);

        addmsg.createOperation(InternalClientConnection.getRootConnection());
      }
      else
      {
        // The first operation should be an ADD operation.
        assertTrue(count != 1,
            "FakeAddOperation was not correctly generated from historical information");
      }
    }

      assertEquals(count, assertCount);
    }

  /**
   * Test the task that purges the replication historical stored in the user
   * entry.
   * Steps :
   * - creates entry containing historical
   * - wait for the purge delay
   * - launch the purge task
   * - verify that all historical has been purged
   *
   * TODO: another test should be written that configures the task no NOT have
   * the time to purge everything in 1 run .. and thus to relaunch it to finish
   * the purge. And verify that the second run starts on the CSN where
   * the previous task run had stopped.
   */
  @Test(enabled=true)
  public void testRecurringPurgeIn1Run() throws Exception
  {
    int entryCount = 10;
    addEntriesWithHistorical(1, entryCount);

    // set the purge delay to 1 minute
    // FIXME could we change this setting to also accept seconds?
    // This way this test would not take one minute to run
    // (and it could also fail less often in jenkins).
    TestCaseUtils.dsconfig(
        "set-replication-domain-prop",
        "--provider-name","Multimaster Synchronization",
        "--domain-name", testName,
        "--set","conflicts-historical-purge-delay:1m");

    // Let's go past the purge delay
    Thread.sleep(60 * 1000);

    // launch the purge
    final int maxWaitTimeInSeconds = 120;
    Entry purgeConflictsHistoricalTask = TestCaseUtils.makeEntry(
        "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
        "objectclass: top",
        "objectclass: ds-task",
        "objectclass: ds-task-purge-conflicts-historical",
        "ds-task-class-name: org.opends.server.tasks.PurgeConflictsHistoricalTask",
        "ds-task-purge-conflicts-historical-domain-dn: " + TEST_ROOT_DN_STRING,
        "ds-task-purge-conflicts-historical-maximum-duration: " + maxWaitTimeInSeconds);
    executeTask(purgeConflictsHistoricalTask, maxWaitTimeInSeconds * 1000);

    // every entry should be purged from its hist
    int expectedNumberOfEntries = 0;
    waitForSearchResult(TEST_ROOT_DN_STRING, WHOLE_SUBTREE, "(ds-sync-hist=*)", SUCCESS, expectedNumberOfEntries);
  }

  /**
   * Add a provided number of generated entries containing historical.
   * @param dnSuffix A suffix to be added to the dn
   * @param entryCnt The number of entries to create
   */
  private void addEntriesWithHistorical(final int dnSuffix, final int entryCnt) throws Exception
  {
    for (int i=0; i<entryCnt;i++)
    {
      String sdn =  "dn: uid=user"+i+dnSuffix+"," + TEST_ROOT_DN_STRING;

        //  Add a test entry.
        TestCaseUtils.addEntry(
            sdn,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user"+i,
            "cn: Aaccf Amar",
            "sn: Amar",
            "givenName: Aaccf",
            "userPassword: password",
            "description: Initial description",
            "displayName: 1"
        );

      // Modify the test entry to give it some history.
      // Test both single and multi-valued attributes.

      String path = TestCaseUtils.createTempFile(
          sdn,
          "changetype: modify",
          "add: cn;lang-en",
          "cn;lang-en: Aaccf Amar",
          "cn;lang-en: Aaccf A Amar",
          "-",
          "replace: givenName",
          "givenName: new given",
          "-",
          "replace: userPassword",
          "userPassword: new pass",
          "-",
          "replace: description",
          "description: replaced description",
          "-",
          "replace: sn",
          "sn: replaced sn",
          "-",
          "add: displayName",
          "displayName: 2",
          "-",
          "delete: displayName",
          "displayName: 1",
          "-"
      );

      String[] args =
      {
          "-h", "127.0.0.1",
          "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
          "-D", "cn=Directory Manager",
          "-w", "password",
          "-f", path
      };

      ldapmodify(args);

      args[9] = TestCaseUtils.createTempFile(
          sdn,
          "changetype: modify",
          "replace: displayName",
          "displayName: 2",
          "-"
      );

      ldapmodify(args);
    }

    for (int i = 0; i < entryCnt; i++)
    {
      DN dn = DN.valueOf("uid=user" + i + dnSuffix + "," + TEST_ROOT_DN_STRING);
      getEntry(dn, 1000, true);
    }
  }

  private void ldapmodify(String[] args)
  {
    assertEquals(LDAPModify.run(nullPrintStream(), System.err, args), 0);
  }
}
