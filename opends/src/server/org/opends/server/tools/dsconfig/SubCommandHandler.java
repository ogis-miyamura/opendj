/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.dsconfig;



import static org.opends.messages.DSConfigMessages.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DurationUnit;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.ManagedObjectPathSerializer;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.SizeUnit;
import org.opends.server.admin.Tag;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.tools.ClientException;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.Menu;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.table.TabSeparatedTablePrinter;
import org.opends.server.util.table.TablePrinter;



/**
 * An interface for sub-command implementations.
 */
abstract class SubCommandHandler implements Comparable<SubCommandHandler> {

  /**
   * A path serializer which is used to retrieve a managed object
   * based on a path and a list of path arguments.
   */
  private class ManagedObjectFinder implements ManagedObjectPathSerializer {

    // The console application.
    private ConsoleApplication app;

    // The index of the next path argument to be retrieved.
    private int argIndex;

    // The list of managed object path arguments.
    private List<String> args;

    private AuthorizationException authze;

    private CommunicationException ce;

    // Any CLI exception that was caught when attempting to find
    // the managed object.
    private CLIException clie;

    private ConcurrentModificationException cme;

    // Any operation exception that was caught when attempting to find
    // the managed object.
    private DefinitionDecodingException dde;

    private ManagedObjectDecodingException mode;

    private ManagedObjectNotFoundException monfe;

    // The current result.
    private MenuResult<ManagedObject<?>> result;



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      if (result.isSuccess()) {
        // We should ignore the "template" name here and use a path
        // argument.
        String childName = args.get(argIndex++);

        try {
          // If the name is null then we must be interactive - so let
          // the user choose.
          if (childName == null) {
            try {
              MenuResult<String> sresult = readChildName(app,
                  result.getValue(), r, d);

              if (sresult.isCancel()) {
                result = MenuResult.cancel();
                return;
              } else if (sresult.isQuit()) {
                result = MenuResult.quit();
                return;
              } else {
                childName = sresult.getValue();
              }
            } catch (CLIException e) {
              clie = e;
              result = MenuResult.quit();
              return;
            }
          } else if (childName.trim().length() == 0) {
            IllegalManagedObjectNameException e =
              new IllegalManagedObjectNameException(childName);
            clie = ArgumentExceptionFactory
                .adaptIllegalManagedObjectNameException(e, d);
            result = MenuResult.quit();
            return;
          }

          ManagedObject<?> child = result.getValue().getChild(r, childName);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            result = MenuResult.quit();
          } else {
            result = MenuResult.<ManagedObject<?>>success(child);
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          result = MenuResult.quit();
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          result = MenuResult.quit();
        } catch (AuthorizationException e) {
          authze = e;
          result = MenuResult.quit();
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          result = MenuResult.quit();
        } catch (ConcurrentModificationException e) {
          cme = e;
          result = MenuResult.quit();
        } catch (CommunicationException e) {
          ce = e;
          result = MenuResult.quit();
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      if (result.isSuccess()) {
        try {
          ManagedObject<?> child = result.getValue().getChild(r);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            result = MenuResult.quit();
          } else {
            result = MenuResult.<ManagedObject<?>>success(child);
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          result = MenuResult.quit();
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          result = MenuResult.quit();
        } catch (AuthorizationException e) {
          authze = e;
          result = MenuResult.quit();
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          result = MenuResult.quit();
        } catch (ConcurrentModificationException e) {
          cme = e;
          result = MenuResult.quit();
        } catch (CommunicationException e) {
          ce = e;
          result = MenuResult.quit();
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      if (result.isSuccess()) {
        try {
          ManagedObject<?> child = result.getValue().getChild(r);

          // Check that child is a sub-type of the specified
          // definition.
          if (!child.getManagedObjectDefinition().isChildOf(d)) {
            clie = ArgumentExceptionFactory.wrongManagedObjectType(r, child
                .getManagedObjectDefinition());
            result = MenuResult.quit();
          } else {
            result = MenuResult.<ManagedObject<?>>success(child);
          }
        } catch (DefinitionDecodingException e) {
          dde = e;
          result = MenuResult.quit();
        } catch (ManagedObjectDecodingException e) {
          mode = e;
          result = MenuResult.quit();
        } catch (AuthorizationException e) {
          authze = e;
          result = MenuResult.quit();
        } catch (ManagedObjectNotFoundException e) {
          monfe = e;
          result = MenuResult.quit();
        } catch (ConcurrentModificationException e) {
          cme = e;
          result = MenuResult.quit();
        } catch (CommunicationException e) {
          ce = e;
          result = MenuResult.quit();
        }
      }
    }



    /**
     * Finds the named managed object.
     *
     * @param app
     *          The console application.
     * @param context
     *          The management context.
     * @param path
     *          The managed object path.
     * @param args
     *          The managed object path arguments.
     * @return Returns a {@link MenuResult#success()} containing the
     *         managed object referenced by the provided managed
     *         object path, or {@link MenuResult#quit()}, or
     *         {@link MenuResult#cancel()}, if the sub-command was
     *         run interactively and the user chose to quit or cancel.
     * @throws CLIException
     *           If one of the naming arguments referenced a managed
     *           object of the wrong type.
     * @throws DefinitionDecodingException
     *           If the managed object was found but its type could
     *           not be determined.
     * @throws ManagedObjectDecodingException
     *           If the managed object was found but one or more of
     *           its properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *           If the requested managed object could not be found on
     *           the server.
     * @throws ConcurrentModificationException
     *           If this managed object has been removed from the
     *           server by another client.
     * @throws AuthorizationException
     *           If the server refuses to retrieve the managed object
     *           because the client does not have the correct
     *           privileges.
     * @throws CommunicationException
     *           If the client cannot contact the server due to an
     *           underlying communication problem.
     */
    public MenuResult<ManagedObject<?>> find(ConsoleApplication app,
        ManagementContext context, ManagedObjectPath<?, ?> path,
        List<String> args) throws CLIException, CommunicationException,
        AuthorizationException, ConcurrentModificationException,
        DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException {
      this.result = MenuResult.<ManagedObject<?>> success(context
          .getRootConfigurationManagedObject());
      this.app = app;
      this.args = args;
      this.argIndex = 0;

      this.clie = null;
      this.authze = null;
      this.ce = null;
      this.cme = null;
      this.dde = null;
      this.mode = null;
      this.monfe = null;

      path.serialize(this);

      if (result.isSuccess()) {
        return result;
      } else if (clie != null) {
        throw clie;
      } else if (authze != null) {
        throw authze;
      } else if (ce != null) {
        throw ce;
      } else if (cme != null) {
        throw cme;
      } else if (dde != null) {
        throw dde;
      } else if (mode != null) {
        throw mode;
      } else if (monfe != null) {
        throw monfe;
      } else {
        // User requested termination interactively.
        return result;
      }
    }
  }



  /**
   * A path serializer which is used to register a sub-command's
   * naming arguments.
   */
  private static class NamingArgumentBuilder implements
      ManagedObjectPathSerializer {

    /**
     * Creates the naming arguments for a given path.
     *
     * @param subCommand
     *          The sub-command.
     * @param path
     *          The managed object path.
     * @param isCreate
     *          Indicates whether the sub-command is a create-xxx
     *          sub-command, in which case the final path element will
     *          have different usage information.
     * @return Returns the naming arguments.
     * @throws ArgumentException
     *           If one or more naming arguments could not be
     *           registered.
     */
    public static List<StringArgument> create(SubCommand subCommand,
        ManagedObjectPath<?, ?> path, boolean isCreate)
        throws ArgumentException {
      NamingArgumentBuilder builder = new NamingArgumentBuilder(subCommand,
          path.size(), isCreate);
      path.serialize(builder);

      if (builder.e != null) {
        throw builder.e;
      }

      return builder.arguments;
    }

    // The list of naming arguments.
    private final List<StringArgument> arguments =
      new LinkedList<StringArgument>();

    // Any argument exception thrown when creating the naming
    // arguments.
    private ArgumentException e = null;

    // Indicates whether the sub-command is a create-xxx
    // sub-command, in which case the final path element will
    // have different usage information.
    private final boolean isCreate;

    // The sub-command.
    private final SubCommand subCommand;

    // The number of path elements to expect.
    private int sz;



    // Private constructor.
    private NamingArgumentBuilder(SubCommand subCommand, int sz,
        boolean isCreate) {
      this.subCommand = subCommand;
      this.sz = sz;
      this.isCreate = isCreate;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d, String name) {
      sz--;

      // Use the last word in the managed object name as the argument
      // prefix.
      StringBuilder builder = new StringBuilder();

      String s = d.getName();
      int i = s.lastIndexOf('-');
      if (i < 0 || i == (s.length() - 1)) {
        builder.append(s);
      } else {
        builder.append(s.substring(i + 1));
      }
      builder.append("-name");
      String argName = builder.toString();
      StringArgument arg;

      try {
        if (isCreate && sz == 0) {
          // The final path element in create-xxx sub-commands should
          // have a different usage.
          PropertyDefinition<?> pd = r.getNamingPropertyDefinition();

          if (pd != null) {
            // Use syntax and description from naming property.
            PropertyDefinitionUsageBuilder b =
              new PropertyDefinitionUsageBuilder(false);
            String usage = "{" + b.getUsage(pd) + "}";
            arg = new StringArgument(argName, null, argName, false, true,
                usage, INFO_DSCFG_DESCRIPTION_NAME_CREATE_EXT.get(d
                    .getUserFriendlyName(), pd.getName(), pd.getSynopsis()));
          } else {
            arg = new StringArgument(argName, null, argName, false, true,
                "{NAME}", INFO_DSCFG_DESCRIPTION_NAME_CREATE.get(d
                    .getUserFriendlyName()));
          }
        } else {
          // A normal naming argument.
          arg = new StringArgument(argName, null, argName, false, true,
              "{NAME}", INFO_DSCFG_DESCRIPTION_NAME.get(
                  d.getUserFriendlyName()));
        }
        subCommand.addArgument(arg);
        arguments.add(arg);
      } catch (ArgumentException e) {
        this.e = e;
      }
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      sz--;
    }



    /**
     * {@inheritDoc}
     */
    public <C extends ConfigurationClient, S extends Configuration>
        void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r,
        AbstractManagedObjectDefinition<C, S> d) {
      sz--;
    }

  }

  /**
   * The threshold above which choice menus should be displayed in
   * multiple columns.
   */
  public static final int MULTI_COLUMN_THRESHOLD = 8;

  /**
   * The value for the long option property.
   */
  private static final String OPTION_DSCFG_LONG_PROPERTY = "property";

  /**
   * The value for the long option record.
   */
  private static final String OPTION_DSCFG_LONG_RECORD = "record";

  /**
   * The value for the long option unit-size.
   */
  private static final String OPTION_DSCFG_LONG_UNIT_SIZE = "unit-size";

  /**
   * The value for the long option unit-time.
   */
  private static final String OPTION_DSCFG_LONG_UNIT_TIME = "unit-time";

  /**
   * The value for the short option property.
   */
  private static final Character OPTION_DSCFG_SHORT_PROPERTY = null;

  /**
   * The value for the short option record.
   */
  private static final char OPTION_DSCFG_SHORT_RECORD = 'E';

  /**
   * The value for the short option unit-size.
   */
  private static final char OPTION_DSCFG_SHORT_UNIT_SIZE = 'z';

  /**
   * The value for the short option unit-time.
   */
  private static final char OPTION_DSCFG_SHORT_UNIT_TIME = 'm';

  // The argument which should be used to specify zero or more
  // property names.
  private StringArgument propertyArgument;

  // The argument which should be used to request record mode.
  private BooleanArgument recordModeArgument;

  // The tags associated with this sub-command handler.
  private final Set<Tag> tags = new HashSet<Tag>();

  // The argument which should be used to request specific size units.
  private StringArgument unitSizeArgument;

  // The argument which should be used to request specific time units.
  private StringArgument unitTimeArgument;



  /**
   * Create a new sub-command handler.
   */
  protected SubCommandHandler() {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public final int compareTo(SubCommandHandler o) {
    String s1 = getSubCommand().getName();
    String s2 = o.getSubCommand().getName();

    return s1.compareTo(s2);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof SubCommandHandler) {
      SubCommandHandler other = (SubCommandHandler) obj;

      String s1 = getSubCommand().getName();
      String s2 = other.getSubCommand().getName();
      return s1.equals(s2);
    } else {
      return false;
    }
  }



  /**
   * Gets the sub-command associated with this handler.
   *
   * @return Returns the sub-command associated with this handler.
   */
  public abstract SubCommand getSubCommand();



  /**
   * Gets the tags associated with this sub-command handler.
   *
   * @return Returns the tags associated with this sub-command
   *         handler.
   */
  public final Set<Tag> getTags() {
    return tags;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode() {
    return getSubCommand().getName().hashCode();
  }



  /**
   * Run this sub-command handler.
   *
   * @param app
   *          The console application.
   * @param factory
   *          The management context factory.
   * @return Returns a {@link MenuResult#success()} containing zero if
   *         the sub-command completed successfully or non-zero if it
   *         did not, or {@link MenuResult#quit()}, or
   *         {@link MenuResult#cancel()}, if the sub-command was run
   *         interactively and the user chose to quit or cancel.
   * @throws ArgumentException
   *           If an argument required by the sub-command could not be
   *           parsed successfully.
   * @throws ClientException
   *           If the management context could not be created.
   * @throws CLIException
   *           If a CLI exception occurred.
   */
  public abstract MenuResult<Integer> run(ConsoleApplication app,
      ManagementContextFactory factory) throws ArgumentException,
      ClientException, CLIException;



  /**
   * Get the string representation of this sub-command handler.
   * <p>
   * The string representation is simply the sub-command's name.
   *
   * @return Returns the string representation of this sub-command
   *         handler.
   */
  @Override
  public final String toString() {
    return getSubCommand().getName();
  }



  /**
   * Adds one or more tags to this sub-command handler.
   *
   * @param tags
   *          The tags to be added to this sub-command handler.
   */
  protected final void addTags(Collection<Tag> tags) {
    this.tags.addAll(tags);
  }



  /**
   * Adds one or more tags to this sub-command handler.
   *
   * @param tags
   *          The tags to be added to this sub-command handler.
   */
  protected final void addTags(Tag... tags) {
    addTags(Arrays.asList(tags));
  }



  /**
   * Creates the naming arguments for a given path and registers them.
   *
   * @param subCommand
   *          The sub-command.
   * @param p
   *          The managed object path.
   * @param isCreate
   *          Indicates whether the sub-command is a create-xxx
   *          sub-command, in which case the final path element will
   *          have different usage information.
   * @return Returns the naming arguments.
   * @throws ArgumentException
   *           If one or more naming arguments could not be
   *           registered.
   */
  protected final List<StringArgument> createNamingArgs(SubCommand subCommand,
      ManagedObjectPath<?, ?> p, boolean isCreate) throws ArgumentException {
    return NamingArgumentBuilder.create(subCommand, p, isCreate);
  }



  /**
   * Creates a script-friendly table printer. This factory method
   * should be used by sub-command handler implementations rather than
   * constructing a table printer directly so that we can easily
   * switch table implementations (perhaps dynamically depending on
   * argument).
   *
   * @param stream
   *          The output stream for the table.
   * @return Returns a script-friendly table printer.
   */
  protected final TablePrinter createScriptFriendlyTablePrinter(
      PrintStream stream) {
    return new TabSeparatedTablePrinter(stream);
  }



  /**
   * Get the managed object referenced by the provided managed object
   * path.
   *
   * @param app
   *          The console application.
   * @param context
   *          The management context.
   * @param path
   *          The managed object path.
   * @param args
   *          The list of managed object names required by the path.
   * @return Returns a {@link MenuResult#success()} containing the
   *         managed object referenced by the provided managed object
   *         path, or {@link MenuResult#quit()}, or
   *         {@link MenuResult#cancel()}, if the sub-command was run
   *         interactively and the user chose to quit or cancel.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   * @throws CLIException
   *           If one of the naming arguments referenced a managed
   *           object of the wrong type.
   * @throws ClientException
   *           If the management context could not be created.
   */
  protected final MenuResult<ManagedObject<?>> getManagedObject(
      ConsoleApplication app, ManagementContext context,
      ManagedObjectPath<?, ?> path, List<String> args) throws CLIException,
      AuthorizationException, DefinitionDecodingException,
      ManagedObjectDecodingException, CommunicationException,
      ConcurrentModificationException, ManagedObjectNotFoundException,
      ClientException {
    ManagedObjectFinder finder = new ManagedObjectFinder();
    return finder.find(app, context, path, args);
  }



  /**
   * Gets the values of the naming arguments.
   *
   * @param app
   *          The console application.
   * @param namingArgs
   *          The naming arguments.
   * @return Returns the values of the naming arguments.
   * @throws ArgumentException
   *           If one of the naming arguments is missing and the
   *           application is non-interactive.
   */
  protected final List<String> getNamingArgValues(ConsoleApplication app,
      List<StringArgument> namingArgs) throws ArgumentException {
    ArrayList<String> values = new ArrayList<String>(namingArgs.size());
    for (StringArgument arg : namingArgs) {
      String value = arg.getValue();

      if (value == null && !app.isInteractive()) {
        throw ArgumentExceptionFactory
            .missingMandatoryNonInteractiveArgument(arg);
      } else {
        values.add(value);
      }
    }
    return values;
  }



  /**
   * Gets the optional list of property names that the user requested.
   *
   * @return Returns the optional list of property names that the user
   *         requested.
   */
  protected final Set<String> getPropertyNames() {
    if (propertyArgument != null) {
      return new LinkedHashSet<String>(propertyArgument.getValues());
    } else {
      return Collections.emptySet();
    }
  }



  /**
   * Gets the optional size unit that the user requested.
   *
   * @return Returns the size unit that the user requested, or
   *         <code>null</code> if no size unit was specified.
   * @throws ArgumentException
   *           If the user specified an invalid size unit.
   */
  protected final SizeUnit getSizeUnit() throws ArgumentException {
    if (unitSizeArgument != null) {
      String value = unitSizeArgument.getValue();

      if (value != null) {
        try {
          return SizeUnit.getUnit(value);
        } catch (IllegalArgumentException e) {
          Message msg = INFO_DSCFG_ERROR_SIZE_UNIT_UNRECOGNIZED.get(value);
          throw new ArgumentException(msg);
        }
      }
    }

    return null;
  }



  /**
   * Gets the optional time unit that the user requested.
   *
   * @return Returns the time unit that the user requested, or
   *         <code>null</code> if no time unit was specified.
   * @throws ArgumentException
   *           If the user specified an invalid time unit.
   */
  protected final DurationUnit getTimeUnit() throws ArgumentException {
    if (unitTimeArgument != null) {
      String value = unitTimeArgument.getValue();

      if (value != null) {
        try {
          return DurationUnit.getUnit(value);
        } catch (IllegalArgumentException e) {
          Message msg = INFO_DSCFG_ERROR_TIME_UNIT_UNRECOGNIZED.get(value);
          throw new ArgumentException(msg);
        }
      }
    }

    return null;
  }



  /**
   * Determines whether the user requested record-mode.
   *
   * @return Returns <code>true</code> if the user requested
   *         record-mode.
   */
  protected final boolean isRecordMode() {
    if (recordModeArgument != null) {
      return recordModeArgument.isPresent();
    } else {
      return false;
    }
  }



  /**
   * Interactively prompts the user to select from a choice of child
   * managed objects.
   * <p>
   * This method will adapt according to the available choice. For
   * example, if there is only one choice, then a question will be
   * asked. If there are no children then an
   * <code>ArgumentException</code> will be thrown.
   *
   * @param <C>
   *          The type of child client configuration.
   * @param <S>
   *          The type of child server configuration.
   * @param app
   *          The console application.
   * @param parent
   *          The parent managed object.
   * @param r
   *          The relation between the parent and the children.
   * @param d
   *          The type of child managed object to choose from.
   * @return Returns a {@link MenuResult#success()} containing the
   *         name of the managed object that the user selected, or
   *         {@link MenuResult#quit()}, or
   *         {@link MenuResult#cancel()}, if the sub-command was run
   *         interactive and the user chose to quit or cancel.
   * @throws CommunicationException
   *           If the server cannot be contacted.
   * @throws ConcurrentModificationException
   *           If the parent managed object has been deleted.
   * @throws AuthorizationException
   *           If the children cannot be listed due to an
   *           authorization failure.
   * @throws CLIException
   *           If the user input can be read from the console or if
   *           there are no children.
   */
  protected final <C extends ConfigurationClient, S extends Configuration>
      MenuResult<String> readChildName(
      ConsoleApplication app, ManagedObject<?> parent,
      InstantiableRelationDefinition<C, S> r,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws AuthorizationException, ConcurrentModificationException,
      CommunicationException, CLIException {
    if (d == null) {
      d = r.getChildDefinition();
    }

    app.println();
    app.println();
    String[] children = parent.listChildren(r, d);
    switch (children.length) {
    case 0: {
      // No options available - abort.
      Message msg =
          ERR_DSCFG_ERROR_FINDER_NO_CHILDREN.get(d.getUserFriendlyPluralName());
      throw new CLIException(msg);
    }
    case 1: {
      // Only one option available so confirm that the user wishes to
      // access it.
      Message msg = INFO_DSCFG_FINDER_PROMPT_SINGLE.get(
              d.getUserFriendlyName(), children[0]);
      if (app.confirmAction(msg, true)) {
        return MenuResult.success(children[0]);
      } else {
        return MenuResult.cancel();
      }
    }
    default: {
      // Display a menu.
      MenuBuilder<String> builder = new MenuBuilder<String>(app);
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
      builder.setPrompt(INFO_DSCFG_FINDER_PROMPT_MANY.get(d
          .getUserFriendlyName()));

      Arrays.sort(children, String.CASE_INSENSITIVE_ORDER);
      for (String child : children) {
        Message option = Message.raw("%s", child);
        builder.addNumberedOption(option, MenuResult.success(child));
      }

      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(true);
      }
      builder.addQuitOption();

      Menu<String> menu = builder.toMenu();
      return menu.run();
    }
    }
  }



  /**
   * Registers the property name argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the property name argument could not be registered.
   */
  protected final void registerPropertyNameArgument(SubCommand subCommand)
      throws ArgumentException {
    this.propertyArgument = new StringArgument(OPTION_DSCFG_LONG_PROPERTY,
        OPTION_DSCFG_SHORT_PROPERTY, OPTION_DSCFG_LONG_PROPERTY, false, true,
        true, "{PROP}", null, null, INFO_DSCFG_DESCRIPTION_PROP.get());
    subCommand.addArgument(propertyArgument);
  }



  /**
   * Registers the record mode argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the record mode argument could not be registered.
   */
  protected final void registerRecordModeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.recordModeArgument = new BooleanArgument(OPTION_DSCFG_LONG_RECORD,
        OPTION_DSCFG_SHORT_RECORD, OPTION_DSCFG_LONG_RECORD,
        INFO_DSCFG_DESCRIPTION_RECORD.get());
    subCommand.addArgument(recordModeArgument);
  }



  /**
   * Registers the unit-size argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the unit-size argument could not be registered.
   */
  protected final void registerUnitSizeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.unitSizeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_SIZE,
        OPTION_DSCFG_SHORT_UNIT_SIZE, OPTION_DSCFG_LONG_UNIT_SIZE, false, true,
        "{UNIT}", INFO_DSCFG_DESCRIPTION_UNIT_SIZE.get());

    subCommand.addArgument(unitSizeArgument);
  }



  /**
   * Registers the unit-time argument with the sub-command.
   *
   * @param subCommand
   *          The sub-command.
   * @throws ArgumentException
   *           If the unit-time argument could not be registered.
   */
  protected final void registerUnitTimeArgument(SubCommand subCommand)
      throws ArgumentException {
    this.unitTimeArgument = new StringArgument(OPTION_DSCFG_LONG_UNIT_TIME,
        OPTION_DSCFG_SHORT_UNIT_TIME, OPTION_DSCFG_LONG_UNIT_TIME, false, true,
        "{UNIT}", INFO_DSCFG_DESCRIPTION_UNIT_TIME.get());

    subCommand.addArgument(unitTimeArgument);
  }



}
