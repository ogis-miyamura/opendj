/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 *      Portions Copyrighted 2019 OGIS-RI Co., Ltd.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.DigestMD5SASLMechanismHandlerCfgDefn;
import org.opends.server.admin.std.server.DigestMD5SASLMechanismHandlerCfg;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;
import org.testng.Assert;

/**
 * A set of test cases for the DIGEST-MD5 SASL mechanism handler.
 */
public class DigestMD5SASLMechanismHandlerTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();

    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--set", "server-fqdn:" + "127.0.0.1");
  }


  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
    TestCaseUtils.dsconfig(
            "set-sasl-mechanism-handler-prop",
            "--handler-name", "DIGEST-MD5",
            "--remove", "server-fqdn:" + "127.0.0.1");
  }


  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: not a DN",
         "",
         "dn: cn=DIGEST-MD5,cn=SASL Mechanisms,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-sasl-mechanism-handler",
         "objectClass: ds-cfg-digest-md5-sasl-mechanism-handler",
         "cn: DIGEST-MD5",
         "ds-cfg-java-class: org.opends.server.extensions." +
              "DigestMD5SASLMechanismHandler",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: cn=does not exist");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the handler with invalid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    DigestMD5SASLMechanismHandlerCfg configuration =
       AdminTestCaseUtils.getConfiguration(
            DigestMD5SASLMechanismHandlerCfgDefn.getInstance(),
            e);

    DigestMD5SASLMechanismHandler handler = new DigestMD5SASLMechanismHandler();
    handler.initializeSASLMechanismHandler(configuration);
  }



  /**
   * Tests the <CODE>isPasswordBased</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testIsPasswordBased()
         throws Exception
  {
    DigestMD5SASLMechanismHandler handler =
         (DigestMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);

    assertTrue(handler.isPasswordBased(SASL_MECHANISM_DIGEST_MD5));
  }



  /**
   * Tests the <CODE>isSecure</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testIsSecure()
         throws Exception
  {
    DigestMD5SASLMechanismHandler handler =
         (DigestMD5SASLMechanismHandler)
         DirectoryServer.getSASLMechanismHandler(SASL_MECHANISM_DIGEST_MD5);

    assertTrue(handler.isSecure(SASL_MECHANISM_DIGEST_MD5));
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindSuccessWithUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:test.user",
      "-o", "authzid=u:test.user",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindSuccessWithDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:uid=test.user,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID with the wrong password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailWrongPasswordWithUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
              "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:test.user",
      "-o", "authzid=u:test.user",
      "-w", "wrongpassword",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the wrong password.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailWrongPasswordWithDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:uid=test.user,o=test",
      "-w", "wrongpassword",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the u: form of the
   * authentication ID with a stored password that's not reversible.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:test.user",
      "-o", "authzid=u:test.user",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with a stored password that's not reversible.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:uid=test.user,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with an invalid DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailInvalidDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:invaliddn",
      "-o", "authzid=dn:invaliddn",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the DN of a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNoSuchUserForUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:doesntexist",
      "-o", "authzid=u:doesntexist",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the DN of a user that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNoSuchUserForDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
         "dn: uid=test.user,o=test",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "uid: test.user",
         "givenName: Test",
         "sn: User",
         "cn: Test User",
         "userPassword: password");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=doesntexist,o=test",
      "-o", "authzid=dn:uid=doesntexist,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with an empty UID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyUID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:",
      "-o", "authzid=u:",
      "-w", "",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the null DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNullDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:",
      "-o", "authzid=dn:",
      "-w", "",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an empty authentication
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyAuthID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=",
      "-o", "authzid=",
      "-w", "",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an empty authorization
   * ID.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailEmptyAuthzID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the root DN (which has a stored password that's not
   * reversible).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailIrreversiblePasswordWithRootDN()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Directory Manager",
      "-o", "authzid=dn:cn=Directory Manager",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a successful LDAP bind using DIGEST-MD5 using the dn: form of the
   * authentication ID with the root DN (which has a stored password that is
   * reversible).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulBindReversiblePasswordWithRootDN()
         throws Exception
  {
    Entry e = TestCaseUtils.addEntry(
         "dn: cn=Second Root DN,cn=Root DNs,cn=config",
         "objectClass: top",
         "objectClass: person",
         "objectClass: organizationalPerson",
         "objectClass: inetOrgPerson",
         "objectClass: ds-cfg-root-dn-user",
         "givenName: Second",
         "sn: Root DN",
         "cn: Second Root DN",
         "ds-cfg-alternate-bind-dn: cn=Second Root DN",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Second Root DN",
      "-o", "authzid=dn:cn=Second Root DN",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);


    DeleteOperation deleteOperation = getRootConnection().processDelete(e.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains the DN of an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNonexistentAuthzDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:uid=nonexistent,o=test",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains a username for an entry that doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailNonexistentAuthzUsername()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=u:nonexistent",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Performs a failed LDAP bind using DIGEST-MD5 using an authorization ID that
   * contains a malformed DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDAPBindFailMalformedAuthzDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:uid=test.user,o=test",
      "-o", "authzid=dn:malformed",
      "-w", "password",
      "-b", "",
      "-s", "base",
      "(objectClass=*)"
    };
    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Verifies that the server will reject a DIGEST-MD5 bind in which the first
   * message contains SASL credentials (which isn't allowed).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testOutOfSequenceBind()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5,
                              ByteString.valueOfUtf8("invalid"));
    Assert.assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Verifies that the server will reject a DIGEST-MD5 bind with malformed
   * credentials.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMalformedCredentials()
         throws Exception
  {
    InternalClientConnection conn =
         new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5, null);
    assertEquals(bindOperation.getResultCode(), ResultCode.SASL_BIND_IN_PROGRESS);

    bindOperation =
         conn.processSASLBind(DN.rootDN(), SASL_MECHANISM_DIGEST_MD5,
                              ByteString.valueOfUtf8("malformed"));
    Assert.assertNotEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }
}
