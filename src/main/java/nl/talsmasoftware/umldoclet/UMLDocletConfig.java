/*
 * Copyright (C) 2016 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.talsmasoftware.umldoclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.tools.doclets.standard.Standard;
import nl.talsmasoftware.umldoclet.config.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static nl.talsmasoftware.umldoclet.rendering.Renderer.isDeprecated;

/**
 * Class containing all possible Doclet options for the UML doclet.
 * This configuration class is also responsible for providing suitable default values in a central location.
 * <p/>
 * TODO: this class needs to be refactored and the String[] representation needs to be replaced by the configured type.
 *
 * @author <a href="mailto:info@talsma-software.nl">Sjoerd Talsma</a>
 */
public class UMLDocletConfig extends EnumMap<UMLDocletConfig.Setting, Object> implements Cloneable, Closeable {
    private static final String UML_ROOTLOGGER_NAME = UMLDoclet.class.getPackage().getName();
    private static final Logger LOGGER = Logger.getLogger(UMLDocletConfig.class.getName());

    public enum Setting {
        UML_LOGLEVEL(new StringSetting("umlLogLevel"), "INFO"),
        UML_INDENTATION(new IntegerSetting("umlIndentation"), "-1"),
        UML_BASE_PATH(new StringSetting("umlBasePath"), null),
        UML_FILE_EXTENSION(new StringSetting("umlFileExtension"), ".puml"),
        UML_FILE_ENCODING(new StringSetting("umlFileEncoding"), "UTF-8"),
        UML_SKIP_STANDARD_DOCLET("umlSkipStandardDoclet", false),
        UML_INCLUDE_PRIVATE_FIELDS("umlIncludePrivateFields", false),
        UML_INCLUDE_PACKAGE_PRIVATE_FIELDS("umlIncludePackagePrivateFields", false),
        UML_INCLUDE_PROTECTED_FIELDS("umlIncludeProtectedFields", true),
        UML_INCLUDE_PUBLIC_FIELDS("umlIncludePublicFields", true),
        UML_INCLUDE_DEPRECATED_FIELDS("umlIncludeDeprecatedFields", false),
        UML_INCLUDE_FIELD_TYPES("umlIncludeFieldTypes", true),
        UML_INCLUDE_METHOD_PARAM_NAMES("umlIncludeMethodParamNames", false),
        UML_INCLUDE_METHOD_PARAM_TYPES("umlIncludeMethodParamTypes", true),
        UML_INCLUDE_METHOD_RETURNTYPES("umlIncludeMethodReturntypes", true),
        UML_INCLUDE_CONSTRUCTORS("umlIncludeConstructors", true),
        UML_INCLUDE_DEFAULT_CONSTRUCTORS("umlIncludeDefaultConstructors", false),
        UML_INCLUDE_PRIVATE_METHODS("umlIncludePrivateMethods", false),
        UML_INCLUDE_PACKAGE_PRIVATE_METHODS("umlIncludePackagePrivateMethods", false),
        UML_INCLUDE_PROTECTED_METHODS("umlIncludeProtectedMethods", true),
        UML_INCLUDE_PUBLIC_METHODS("umlIncludePublicMethods", true),
        UML_INCLUDE_DEPRECATED_METHODS("umlIncludeDeprecatedMethods", false),
        UML_INCLUDE_ABSTRACT_SUPERCLASS_METHODS("umlIncludeAbstractSuperclassMethods", true),
        UML_INCLUDE_PRIVATE_CLASSES("umlIncludePrivateClasses", false),
        UML_INCLUDE_PACKAGE_PRIVATE_CLASSES("umlIncludePackagePrivateClasses", true),
        UML_INCLUDE_PROTECTED_CLASSES("umlIncludeProtectedClasses", true),
        UML_INCLUDE_DEPRECATED_CLASSES("umlIncludeDeprecatedClasses", false),
        UML_INCLUDE_PRIVATE_INNERCLASSES("umlIncludePrivateInnerClasses", false),
        UML_INCLUDE_PACKAGE_PRIVATE_INNERCLASSES("umlIncludePackagePrivateInnerClasses", false),
        UML_INCLUDE_PROTECTED_INNERCLASSES("umlIncludeProtectedInnerClasses", false),
        UML_EXCLUDED_REFERENCES(new ListSetting("umlExcludedReferences"), "java.lang.Object, java.lang.Enum"),
        UML_INCLUDE_OVERRIDES_FROM_EXCLUDED_REFERENCES("umlIncludeOverridesFromExcludedReferences", false),
        UML_COMMAND(new ListSetting("umlCommand"), "");

        private final AbstractSetting delegate;
        private final String defaultValue;
        private final int optionLength;

        Setting(String name, boolean defaultValue) {
            this(new BooleanSetting(name, defaultValue), Boolean.toString(defaultValue));
        }

        Setting(AbstractSetting delegate, String defaultValue) {
            this(delegate, defaultValue, 2); // By default, declare one option and one parameter string.
        }

        Setting(AbstractSetting delegate, String defaultValue, int optionLength) {
            this.delegate = delegate;
            this.defaultValue = defaultValue;
            this.optionLength = optionLength;
        }

        private static Setting forOption(String... option) {
            if (option != null && option.length > 0) {
                for (Setting setting : values()) {
                    if (setting.delegate.matches(option[0])) {
                        return setting;
                    }
                }
            }
            return null;
        }

        String[] validate(String[] optionValue) {
            if (optionLength != optionValue.length) {
                throw new IllegalArgumentException(String.format(
                        "Expected %s but received %s: %s.",
                        optionLength, optionValue.length, Arrays.toString(optionValue)));
            }
            // TODO MOVE to AbstractSetting API.
            final String value = optionLength > 1 ? optionValue[1].trim() : null;
            if (delegate instanceof BooleanSetting && value != null && !asList("true", "false").contains(value.toLowerCase(Locale.ENGLISH))) {
                throw new IllegalArgumentException(
                        String.format("Expected \"true\" or \"false\", but received \"%s\".", value));
            } else if (delegate instanceof IntegerSetting && value != null && !value.matches("\\d+")) {
                throw new IllegalArgumentException(
                        String.format("Expected a numerical value, but received \"%s\".", value));
            }
            return optionValue;
        }
    }

    private final String defaultBasePath;
    private final String[][] invalidOptions;
    private final String[][] standardOptions;
    private Properties properties;
    private Handler umlLogHandler;

    public UMLDocletConfig(String[][] options, DocErrorReporter reporter) {
        super(Setting.class);
        String basePath = ".";
        try {
            basePath = new File(".").getCanonicalPath();
        } catch (IOException ioe) {
            reporter.printError("Could not determine base path: " + ioe.getMessage());
        }
        this.defaultBasePath = basePath;
        List<String[]> stdOpts = new ArrayList<>(), invalidOpts = new ArrayList<>();
        for (String[] option : options) {
            try {
                // TODO: Refactor into Setting class.
                final Setting setting = Setting.forOption(option);
                if (setting == null) {
                    stdOpts.add(option);
                } else if (setting.delegate instanceof ListSetting) {
                    List<String> values = new ArrayList<>();
                    if (super.containsKey(setting)) {
                        values.addAll((Collection<String>) super.get(setting));
                    }
                    List<String> split = split(setting.validate(option));
                    values.addAll(split.subList(1, split.size()));
                    super.put(setting, values);
                } else {
                    super.put(setting, setting.validate(option)[1]);
                }
            } catch (RuntimeException invalid) {
                reporter.printError(String.format("Invalid option \"%s\". %s", option[0], invalid.getMessage()));
                invalidOpts.add(option);
            }
        }
        this.standardOptions = stdOpts.toArray(new String[stdOpts.size()][]);
        this.invalidOptions = invalidOpts.toArray(new String[invalidOpts.size()][]);
        initializeUmlLogging();
    }

    private Properties properties() {
        if (properties == null) {
            properties = new Properties();
            try (InputStream in = getClass().getResourceAsStream("/META-INF/umldoclet.properties")) {
                properties.load(in);
            } catch (IOException ioe) {
                throw new IllegalStateException("I/O exception loading properties: " + ioe.getMessage(), ioe);
            }
        }
        return properties;
    }

    String stringValue(Setting setting, String... standardOpts) {
        String value = Objects.toString(super.get(setting), null);
        for (int i = 0; value == null && i < standardOpts.length; i++) {
            for (int j = 0; j < standardOptions.length; j++) {
                if (standardOptions[j].length > 1
                        && standardOpts[i].equalsIgnoreCase(standardOptions[j][0])) {
                    value = standardOptions[j][1];
                    LOGGER.log(Level.FINEST, "Using standard option \"{0}\" for delegate \"{1}\": \"{2}\".",
                            new Object[]{standardOpts[i], setting, value});
                    break;
                }
            }
        }
        return Objects.toString(value, setting.defaultValue);
    }

    List<String> stringValues(Setting setting) {
        if (super.containsKey(setting)) {
            Object value = super.get(setting);
            if (value instanceof List) {
                return (List<String>) value;
            }
            return split(value.toString());
        }
        return split(setting.defaultValue);
    }

    public String version() {
        return properties().getProperty("version");
    }

    public Level umlLogLevel() {
        final String level = stringValue(Setting.UML_LOGLEVEL).toUpperCase(Locale.ENGLISH);
        switch (level) {
            case "ALL":
            case "TRACE":
                return Level.FINEST;
            case "DEBUG":
                return Level.FINE;
            case "WARN":
                return Level.WARNING;
            case "ERROR":
            case "FATAL":
                return Level.SEVERE;
            default:
                return Level.parse(level);
        }
    }

    /**
     * @return The base path where the documentation should be created.
     */
    public String basePath() {
        return Objects.toString(stringValue(Setting.UML_BASE_PATH), defaultBasePath);
    }

    /**
     * @return The indentation (in number of spaces) to use for generated UML files
     * (defaults to {@code -1} which leaves the indentation unspecified).
     */
    public int indentation() {
        return Integer.valueOf(stringValue(Setting.UML_INDENTATION));
    }

    /**
     * @return The file extension for the PlantUML files (defaults to {@code ".puml"}).
     */
    public String umlFileExtension() {
        final String extension = stringValue(Setting.UML_FILE_EXTENSION);
        return extension.startsWith(".") ? extension : "." + extension;
    }

    /**
     * @return The file character encoding for the PlantUML files (defaults to {@code "UTF-8"}).
     */
    public String umlFileEncoding() {
        return stringValue(Setting.UML_FILE_ENCODING, "-docEncoding");
    }

    public boolean skipStandardDoclet() {
        return Boolean.valueOf(stringValue(Setting.UML_SKIP_STANDARD_DOCLET));
    }

    /**
     * @return Whether or not to include private fields in the UML diagrams (defaults to {@code false}).
     */
    public boolean includePrivateFields() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PRIVATE_FIELDS));
    }

    /**
     * @return Whether or not to include package-private fields in the UML diagrams (defaults to {@code false}).
     */
    public boolean includePackagePrivateFields() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PACKAGE_PRIVATE_FIELDS));
    }

    /**
     * @return Whether or not to include private fields in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeProtectedFields() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PROTECTED_FIELDS));
    }

    /**
     * @return Whether or not to include public fields in the UML diagrams (defaults to {@code true}).
     */
    public boolean includePublicFields() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PUBLIC_FIELDS));
    }

    /**
     * @return Whether or not to include deprecated fields in the UML diagrams (defaults to {@code false}).
     */
    public boolean includeDeprecatedFields() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_DEPRECATED_FIELDS));
    }

    /**
     * @return Whether or not to include field type details in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeFieldTypes() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_FIELD_TYPES));
    }

    /**
     * This configuration delegate cannot be directly provided via a single option.
     * This is a combination of the {@code "-umlIncludeMethodParamNames"} OR {@code "-umlIncludeMethodParamTypes"}.
     *
     * @return Whether or not to include method parameters in the UML diagrams (either by name, type or both).
     */
    public boolean includeMethodParams() {
        return includeMethodParamNames() || includeMethodParamTypes();
    }

    /**
     * @return Whether or not to include method parameter names in the UML diagrams (defaults to {@code false}).
     */
    public boolean includeMethodParamNames() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_METHOD_PARAM_NAMES));
    }

    /**
     * @return Whether or not to include method parameter types in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeMethodParamTypes() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_METHOD_PARAM_TYPES));
    }

    public boolean includeMethodReturntypes() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_METHOD_RETURNTYPES));
    }

    /**
     * Please note that even when constructors are included, they are either rendered or not, based on the various
     * method visibility settings such as {@code "-includePrivateMethods", "-includePackagePrivateMethods",
     * "-includeProtectedMethods"} and {@code "-includePublicMethods"}.
     *
     * @return Whether or not to include any constructors in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeConstructors() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_CONSTRUCTORS));
    }

    public boolean includeDefaultConstructors() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_DEFAULT_CONSTRUCTORS));
    }

    /**
     * @return Whether or not to include private methods in the UML diagrams (defaults to {@code false}).
     */
    public boolean includePrivateMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PRIVATE_METHODS));
    }

    /**
     * @return Whether or not to include package-private methods in the UML diagrams (defaults to {@code false}).
     */
    public boolean includePackagePrivateMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PACKAGE_PRIVATE_METHODS));
    }

    /**
     * @return Whether or not to include private methods in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeProtectedMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PROTECTED_METHODS));
    }

    /**
     * @return Whether or not to include public methods in the UML diagrams (defaults to {@code true}).
     */
    public boolean includePublicMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PUBLIC_METHODS));
    }

    /**
     * @return Whether or not to include deprecated methods in the UML diagrams (defaults to {@code false}).
     */
    public boolean includeDeprecatedMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_DEPRECATED_METHODS));
    }

    /**
     * @return Whether or not to include abstract methods from interfaces and abstract classes
     * (from referenced external packages) in the UML diagrams (defaults to {@code true}).
     */
    public boolean includeAbstractSuperclassMethods() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_ABSTRACT_SUPERCLASS_METHODS));
    }

    private boolean includePrivateClasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PRIVATE_CLASSES));
    }

    private boolean includePackagePrivateClasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PACKAGE_PRIVATE_CLASSES));
    }

    private boolean includeProtectedClasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PROTECTED_CLASSES));
    }

    private boolean includeDeprecatedClasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_DEPRECATED_CLASSES));
    }

    private boolean includePrivateInnerclasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PRIVATE_INNERCLASSES));
    }

    private boolean includePackagePrivateInnerclasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PACKAGE_PRIVATE_INNERCLASSES));
    }

    private boolean includeProtectedInnerclasses() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_PROTECTED_INNERCLASSES));
    }

    public boolean includeClass(ClassDoc classDoc) {
        if (classDoc == null) {
            LOGGER.log(Level.WARNING, "Encountered <null> class documentation!");
            return false;
        }
        boolean included = true;
        final boolean isInnerclass = classDoc.containingClass() != null;
        if (classDoc.isPrivate() && (!includePrivateClasses() || (isInnerclass && !includePrivateInnerclasses()))) {
            LOGGER.log(Level.FINEST, "Not including private class \"{0}\".", classDoc.qualifiedName());
            included = false;
        } else if (classDoc.isPackagePrivate()
                && (!includePackagePrivateClasses() || isInnerclass && !includePackagePrivateInnerclasses())) {
            LOGGER.log(Level.FINER, "Not including package-private class \"{0}\".", classDoc.qualifiedName());
            included = false;
        } else if (classDoc.isProtected()
                && (!includeProtectedClasses() || isInnerclass && !includeProtectedInnerclasses())) {
            LOGGER.log(Level.FINE, "Not including protected class \"{0}\".", classDoc.qualifiedName());
            included = false;
        } else if (isDeprecated(classDoc) && !includeDeprecatedClasses()) {
            LOGGER.log(Level.FINE, "Not including deprecated class \"{0}\".", classDoc.qualifiedName());
            included = false;
        }

        LOGGER.log(Level.FINEST, "{0} class \"{1}\".",
                new Object[]{included ? "Including" : "Not including", classDoc.qualifiedName()});
        return included;
    }

    private Collection<String> excludedReferences = null;

    /**
     * @return The excluded references which should not be rendered.
     */
    public synchronized Collection<String> excludedReferences() {
        if (excludedReferences == null) {
            excludedReferences = stringValues(Setting.UML_EXCLUDED_REFERENCES);
            LOGGER.log(Level.FINEST, "Excluding the following references: {0}.", excludedReferences);
        }
        return excludedReferences;
    }

    /**
     * @return Whether or not to include overridden methods declared by excluded references
     * (i.e. include java.lang.Object methods?), defaults to {@code false}.
     */
    public boolean includeOverridesFromExcludedReferences() {
        return Boolean.valueOf(stringValue(Setting.UML_INCLUDE_OVERRIDES_FROM_EXCLUDED_REFERENCES));
    }

    public List<String> umlCommands() {
        return stringValues(Setting.UML_COMMAND);
    }

    public boolean supportLegacyTags() {
        return true;
    }

    public static int optionLength(String option) {
        final Setting setting = Setting.forOption(option);
        return setting == null ? Standard.optionLength(option) : setting.optionLength;
    }

    public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
        try (UMLDocletConfig config = new UMLDocletConfig(options, reporter)) {
            return Standard.validOptions(config.standardOptions, reporter)
                    && config.invalidOptions.length == 0;
        }
    }

    private void initializeUmlLogging() {
        // Clear levels on any previously instantiated sub-loggers.
        for (Enumeration<String> en = LogManager.getLogManager().getLoggerNames(); en.hasMoreElements(); ) {
            String loggerName = en.nextElement();
            if (loggerName.startsWith(UML_ROOTLOGGER_NAME)) {
                Logger.getLogger(loggerName).setLevel(null);
            }
        }
        // Configure the umldoclet root logger.
        this.umlLogHandler = new UmlLogHandler();
        Logger.getLogger(UML_ROOTLOGGER_NAME).setLevel(this.umlLogLevel());
        Logger.getLogger(UML_ROOTLOGGER_NAME).addHandler(this.umlLogHandler);
    }

    @Override
    public synchronized void close() {
        if (umlLogHandler != null) {
            Logger.getLogger(UML_ROOTLOGGER_NAME).removeHandler(umlLogHandler);
            umlLogHandler = null;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(getClass().getSimpleName())
                .append(super.toString())
                .append(",StandardOptions{");
        String sep = "";
        for (String[] option : standardOptions) {
            if (option.length > 0) {
                result.append(sep).append(option[0]);
                if (option.length > 1) result.append(":").append(option[1]);
                for (int i = 2; i < option.length; i++) result.append(' ').append(option[i]);
                sep = ", ";
            }
        }
        return result.append('}').toString();
    }

    private static class UmlLogHandler extends ConsoleHandler {
        private UmlLogHandler() {
            super.setLevel(Level.ALL);
            super.setOutputStream(System.out);
            super.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("%s%n", super.formatMessage(record));
                }
            });
        }
    }

    private static List<String> split(String... values) {
        List<String> result = emptyList();
        for (String value : values) {
            for (String elem : value.split("[,;\\n]")) {
                elem = elem.trim();
                if (!elem.isEmpty()) {
                    result = add(result, elem.trim());
                }
            }
        }
        return result;
    }

    private static List<String> add(List<String> list, String elem) {
        switch (list.size()) {
            case 0:
                return singletonList(elem);
            case 1:
                String curr = list.get(0);
                list = new ArrayList<>();
                list.add(curr);
        }
        list.add(elem);
        return list;
    }

}
