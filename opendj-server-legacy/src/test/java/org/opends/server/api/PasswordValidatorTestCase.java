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
 *      Portions Copyright 2011-2015 ForgeRock AS.
 *      Portions Copyrighted 2019 OGIS-RI Co., Ltd.
 */
package org.opends.server.api;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.opends.server.TestCaseUtils;
import org.opends.server.extensions.TestPasswordValidator;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.tools.LDAPWriter;
import org.opends.server.types.RawModification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;
import org.testng.Assert;

/**
 * A set of generic test cases for password validators.
 */
public class PasswordValidatorTestCase
       extends APITestCase
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
    TestCaseUtils.restartServer();
  }



  /**
   * Drops static references to allow garbage collection.
   */
  @AfterClass
  public void shutdown()
  {
    TestPasswordValidator.clearInstanceAfterTests();
  }



  /**
   * Gets simple test coverage for the default
   * PasswordValidator.finalizePasswordValidator method.
   */
  @Test
  public void testFinalizePasswordValidator()
  {
    TestPasswordValidator.getInstance().finalizePasswordValidator();
  }



  /**
   * Performs a test to ensure that the password validation will be successful
   * under the base conditions for the password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulValidationPasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    assertEquals(TestPasswordValidator.getLastNewPassword(),
                 ByteString.valueOfUtf8("newPassword"));
    assertFalse(TestPasswordValidator.getLastCurrentPasswords().isEmpty());
  }



  /**
   * Performs a test to ensure that the password validation will fail if the
   * test validator is configured to make it fail for the password modify
   * extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailedValidationPasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    TestPasswordValidator.setNextReturnValue(false);
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    int returnCode = LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                           null);
    Assert.assertNotEquals(returnCode, 0);

    assertEquals(TestPasswordValidator.getLastNewPassword(),
                 ByteString.valueOfUtf8("newPassword"));
    assertFalse(TestPasswordValidator.getLastCurrentPasswords().isEmpty());

    TestPasswordValidator.setNextReturnValue(true);
  }



  /**
   * Performs a test to make sure that the clear-text password will not be
   * provided if the user has a non-reversible scheme and does not provide the
   * current password for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCurrentPasswordNotAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertTrue(currentPasswords.isEmpty(), "currentPasswords=" + currentPasswords);
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a non-reversible scheme but provides the current password
   * for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCurrentPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and does not provide the current
   * password for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStoredPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and also provides the current password
   * for a password modify extended operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStoredAndCurrentPasswordAvailablePasswordModifyExtOp()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null),
                 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }



  /**
   * Performs a test to ensure that the password validation will be successful
   * under the base conditions for the modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSuccessfulValidationModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               ByteString.valueOfUtf8("uid=test.user,o=test"),
                                3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    LDAPAttribute attr = new LDAPAttribute("userPassword", "newPassword");
    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  ByteString.valueOfUtf8("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    assertEquals(TestPasswordValidator.getLastNewPassword(),
                 ByteString.valueOfUtf8("newPassword"));
    assertTrue(TestPasswordValidator.getLastCurrentPasswords().isEmpty());
  }



  /**
   * Performs a test to ensure that the password validation will fail if the
   * test validator is configured to make it fail for the modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testFailedValidationModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               ByteString.valueOfUtf8("uid=test.user,o=test"),
                                3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    ArrayList<RawModification> mods = new ArrayList<>();
    LDAPAttribute attr = new LDAPAttribute("userPassword", "newPassword");
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    TestPasswordValidator.setNextReturnValue(false);
    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  ByteString.valueOfUtf8("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    Assert.assertNotEquals(modifyResponse.getResultCode(), 0);

    assertEquals(TestPasswordValidator.getLastNewPassword(),
                 ByteString.valueOfUtf8("newPassword"));
    assertTrue(TestPasswordValidator.getLastCurrentPasswords().isEmpty());

    TestPasswordValidator.setNextReturnValue(true);
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a non-reversible scheme but provides the current password
   * for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCurrentPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               ByteString.valueOfUtf8("uid=test.user,o=test"),
                                3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    LDAPAttribute attr = new LDAPAttribute("userPassword", "password");
    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    attr = new LDAPAttribute("userPassword", "newPassword");
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  ByteString.valueOfUtf8("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and does not provide the current
   * password for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStoredPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               ByteString.valueOfUtf8("uid=test.user,o=test"),
                                3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    LDAPAttribute attr = new LDAPAttribute("userPassword", "newPassword");
    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.REPLACE, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  ByteString.valueOfUtf8("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }



  /**
   * Performs a test to make sure that the clear-text password will be provided
   * if the user has a reversible scheme and also provides the current password
   * for a modify operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testStoredAndCurrentPasswordAvailableModify()
         throws Exception
  {
    TestPasswordValidator.setNextReturnValue(true);
    TestPasswordValidator.setNextInvalidReason(null);

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
         "ds-privilege-name: bypass-acl",
         "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
              "cn=Password Policies,cn=config");


    Socket s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    org.opends.server.tools.LDAPReader r = new org.opends.server.tools.LDAPReader(s);
    LDAPWriter w = new LDAPWriter(s);
    TestCaseUtils.configureSocket(s);

    BindRequestProtocolOp bindRequest =
      new BindRequestProtocolOp(
               ByteString.valueOfUtf8("uid=test.user,o=test"),
                                3, ByteString.valueOfUtf8("password"));
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    w.writeMessage(message);

    message = r.readMessage();
    BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
    assertEquals(bindResponse.getResultCode(), 0);

    LDAPAttribute attr = new LDAPAttribute("userPassword", "password");
    ArrayList<RawModification> mods = new ArrayList<>();
    mods.add(new LDAPModification(ModificationType.DELETE, attr));

    attr = new LDAPAttribute("userPassword", "newPassword");
    mods.add(new LDAPModification(ModificationType.ADD, attr));

    ModifyRequestProtocolOp modifyRequest =
         new ModifyRequestProtocolOp(
                  ByteString.valueOfUtf8("uid=test.user,o=test"), mods);
    message = new LDAPMessage(2, modifyRequest);
    w.writeMessage(message);

    message = r.readMessage();
    ModifyResponseProtocolOp modifyResponse =
         message.getModifyResponseProtocolOp();
    assertEquals(modifyResponse.getResultCode(), 0);

    Set<ByteString> currentPasswords =
         TestPasswordValidator.getLastCurrentPasswords();
    assertFalse(currentPasswords.isEmpty());
    assertEquals(currentPasswords.size(), 1);
    assertEquals(currentPasswords.iterator().next(),
                 ByteString.valueOfUtf8("password"));
  }
}

