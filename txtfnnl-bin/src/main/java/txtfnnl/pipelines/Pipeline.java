package txtfnnl.pipelines;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.SimplePipeline;
import txtfnnl.tika.sax.CleanBodyContentHandler;
import txtfnnl.tika.sax.ElsevierXMLContentHandler;
import txtfnnl.tika.sax.PubMedCentralXMLContentHandler;
import txtfnnl.tika.sax.XMLContentHandler;
import txtfnnl.tika.uima.AbstractTikaAnnotator;
import txtfnnl.tika.uima.TikaAnnotator;
import txtfnnl.tika.uima.TikaExtractor;
import txtfnnl.uima.Views;
import txtfnnl.uima.analysis_component.opennlp.SentenceAnnotator;
import txtfnnl.uima.collection.DirectoryReader;
import txtfnnl.uima.collection.FileReader;
import txtfnnl.uima.collection.OutputWriter;
import txtfnnl.uima.resource.AuthenticationResourceBuilder;
import txtfnnl.uima.resource.JdbcConnectionResourceImpl;
import txtfnnl.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.*;

/**
 * <b><code>txtfnnl</code></b> pipeline assembly and command line setup.
 * <p/>
 * In the simplest case (only extracts the plain text), a pipeline can be set up as follows:
 * <p/>
 * <pre>
 * Pipeline p = new Pipeline();
 * p.setReader(new File(System.getProperty(&quot;user.dir&quot;)));
 * p.configureTika();
 * p.setConsumer(txtfnnl.uima.collection.TextWriter.configure());
 * p.run();
 * </pre>
 * <p/>
 * This will generate a simple pipeline that reads all files in the CWD ("user.dir"), extracts their
 * content with Tika, and writes that content to STDOUT.
 *
 * @author Florian Leitner
 */
public
class Pipeline {
  private CollectionReader collectionReader;
  private AnalysisEngine[] pipeline;

  /**
   * Add default command-line options for any pipeline. The added options are: <ul>
   * <li><code>R</code>, <code>recursive</code></li> <li><code>mime</code></li> <li><code>h</code>,
   * <code>help</code></li> <li><code>i</code>, <code>info</code></li> <li><code>q</code>,
   * <code>quiet</code></li> <li><code>v</code>, <code>verbose</code></li> </ul>
   *
   * @param opts to expand
   */
  public static
  void addLogHelpAndInputOptions(Options opts) {
    // Reader options
    opts.addOption(
        "R", "recursive", false, "include files in all sub-directories of input directory [false]"
    );
    opts.addOption("mime", true,
                   "define one MIME type for all input files [auto-detect]");
    // Logger options
    opts.addOption("h", "help", false, "show this help document");
    opts.addOption("i", "info", false, "log INFO-level messages [WARN]");
    opts.addOption("q", "quiet", false, "log SEVERE-level messages only [WARN]");
    opts.addOption("v", "verbose", false, "log FINE-level messages [WARN]");
  }

  /**
   * Add standard output options for any pipeline. The added options are: <ul> <li><code>E</code>,
   * <code>output-encoding</code></li> <li><code>o</code>, <code>output-directory</code></li>
   * <li><code>r</code>, <code>replace-files</code></li> </ul>
   *
   * @param opts to expand
   */
  public static
  void addOutputOptions(Options opts) {
    opts.addOption(
        "E", "output-encoding", true, "use encoding [" +
                                      (IOUtils.isMacOSX() ? "UTF-8" : Charset.defaultCharset()) +
                                      "]"
    );
    opts.addOption("o", "output-directory", true, "output directory for writing files [STDOUT]");
    opts.addOption("X", "raw-xmi", false, "output XMI instead of text");
    opts.addOption(
        "r", "replace-files", false, "replace files in the output directory if they exist [false]"
    );
  }

  /**
   * Add Tika command-line options for any pipeline. The added options are <ul> <li><code>e</code>,
   * <code>input-encoding</code></li> <li><code>x</code>, <code>xml-handler</code></li>
   * <li><code>normalgreek</code></li> </ul>
   *
   * @param opts to expand
   */
  public static
  void addTikaOptions(Options opts) {
    opts.addOption("e", "input-encoding", true, "use encoding [detect/guess]");
    opts.addOption("normalgreek", false, "normalize Greek letters in input (to Latin words)");
    opts.addOption(
        "x", "xml-handler", true,
        "select XML handler: 'default' (Tika), 'clean' (skips whitespaces), 'pmc', or 'elsevier'"
    );
  }

  /**
   * Add OpenNLP sentence segmentation command-line options for any pipeline. The added options are
   * <ul> <li><code>s</code>, <code>split-anywhere</code></li> <li><code>S</code>,
   * <code>single-newlines</code></li> <li><code>J</code>, <code>sentence-model</code></li> </ul>
   *
   * @param opts to expand
   */
  public static
  void addSentenceAnnotatorOptions(Options opts) {
    opts.addOption("S", "split-anywhere", false, "do not use newlines for splitting");
    opts.addOption("s", "single-newlines", false, "split sentences on single newlines");
    opts.addOption("sentencemodel", true, "set a different model file (default: en)");
  }

  /**
   * Add the command-line options to use a {@link JdbcConnectionResourceImpl JDBC resource} for any
   * pipeline. The added options are: <ul> <li><code>dbdriver</code></li> <li><code>H</code>,
   * <code>dbhost</code></li> <li><code>dbprovider</code></li> <li><code>d</code>,
   * <code>dbname</code></li> <li><code>dbpassword</code></li> <li><code>dbuser</code></li> </ul>
   *
   * @param opts to expand
   */
  public static
  void addJdbcResourceOptions(Options opts, String driverClass, String provider, String dbName) {
    opts.addOption("dbdriver", true, "class of the DB's JDBC driver [" + driverClass + "]");
    opts.addOption(
        "H", "dbhost", true, "hostname where the DB is running (incl. port) [localhost]"
    );
    opts.addOption("dbprovider", true, "JDBC URL name for that DB [" + provider + "]");
    opts.addOption("d", "dbname", true, "database name in the DB [" + dbName + "]");
    opts.addOption("dbpassword", true, "password for the DB (none)");
    opts.addOption("dbuser", true, "username for the DB (none)");
  }

  /**
   * Set up logging unless the help option (h) is set. If the help option is set, print the help and
   * exit ( {@link System#exit(int)}).
   *
   * @param cmd   command line options (to set the root log level)
   * @param opts  the options setup (to print the help message)
   * @param usage a usage string (to print the help message)
   *
   * @return a configured {@link Pipeline} logger
   */
  public static
  Logger loggingSetup(CommandLine cmd, Options opts, String usage) {
    // if the help option was given, print help and exit
    if (cmd.hasOption('h')) {
      final HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(usage, opts);
      System.out.println("\n(c) Florian Leitner 2012. All rights reserved.");
      System.exit(0); // == EXIT (normally) ==
    }
    // otherwise, set up the logging facilities
    URL loggingProperties = null;
    try {
      if (System.getProperty("java.util.logging.config.file") == null) {
        Class<? extends Thread> threadClass = Thread.currentThread().getClass();
        LogManager.getLogManager().readConfiguration(
            threadClass.getResourceAsStream("/logging.properties")
        );
        loggingProperties = threadClass.getResource("/logging.properties");
      }
    } catch (final SecurityException ex) {
      System.err.println("SecurityException while configuring logging");
      System.err.println(ex.getMessage());
    } catch (final IOException ex) {
      System.err.println("IOException while configuring logging");
      System.err.println(ex.getMessage());
    }
    // set up the root logger
    final Logger rootLogger = LogManager.getLogManager().getLogger("");
    if (cmd.hasOption('q')) {
      rootLogger.setLevel(Level.SEVERE);
    } else if (cmd.hasOption('v')) {
      rootLogger.setLevel(Level.FINE);
    } else if (!cmd.hasOption('i')) {
      rootLogger.setLevel(Level.WARNING);
    } else {
      rootLogger.setLevel(Level.INFO);
    }
    Handler[] handlers = rootLogger.getHandlers();
    for (int index = 0; index < handlers.length; index++) {
      if (handlers[index] instanceof ConsoleHandler)
        handlers[index].setLevel(rootLogger.getLevel());
    }
    final Logger l = Logger.getLogger(Pipeline.class.getName());
    l.log(
        Level.FINE, "logging setup using {0} complete",
        loggingProperties == null ? "an undefined logging.properties resource" :
        loggingProperties.toString()
    );
    return l;
  }

  /** Return the input encoding option value or the <code>file.encoding</code> property if not set. */
  public static
  String inputEncoding(CommandLine cmd) {
    return cmd.getOptionValue('e') == null ? System.getProperty("file.encoding") :
           cmd.getOptionValue('e');
  }

  /**
   * Return a readable output directory or return <code>null</code> if not set. Does a {@link
   * System#exit(int)} with value <code>1</code> if the option isn't either <code>null</code> or a
   * writeable directory path.
   */
  public static
  File outputDirectory(CommandLine cmd) {
    File outputDirectory = null;
    if (cmd.hasOption('o')) {
      outputDirectory = new File(cmd.getOptionValue('o'));
      if (!outputDirectory.isDirectory() || !outputDirectory.canWrite()) {
        System.err.print("cannot write to ");
        System.err.println(outputDirectory.getPath());
        System.exit(1); // == EXIT ==
      }
    }
    return outputDirectory;
  }

  /**
   * Ensure a readable output directory by returning the option value or otherwise use the current
   * user directory.
   * <p/>
   * Does a {@link System#exit(int)} with value <code>1</code> if the directory option is not a
   * writeable directory path.
   */
  public static
  File ensureOutputDirectory(CommandLine cmd) {
    File outputDirectory = new File(System.getProperty("user.dir"));
    if (cmd.hasOption('o')) outputDirectory = outputDirectory(cmd);
    return outputDirectory;
  }

  /** Return <code>true</code> if raw XMI should be produced rather than the standard output format. */
  public static
  boolean rawXmi(CommandLine cmd) {
    return cmd.hasOption('X');
  }

  /** Configure a Writer Builder using the command line options. */
  public static
  <B extends OutputWriter.Builder> B configureWriter(CommandLine cmd, B writer) {
    if (Pipeline.outputEncoding(cmd) != null) writer.setEncoding(Pipeline.outputEncoding(cmd));
    if (Pipeline.outputDirectory(cmd) != null)
      writer.setOutputDirectory(Pipeline.outputDirectory(cmd));
    if (Pipeline.outputOverwriteFiles(cmd)) writer.overwriteFiles();
    return writer;
  }

  /** Return the output encoding option value or <code>null</code> if not set. */
  private static
  String outputEncoding(CommandLine cmd) {
    return cmd.getOptionValue('E');
  }

  /** Check for the replace (overwrite) files command line option. */
  private static
  boolean outputOverwriteFiles(CommandLine cmd) {
    return cmd.hasOption('r');
  }

  /** Get a correctly configured {@link SentenceAnnotator} wrt. the command line options. */
  public static
  AnalysisEngineDescription getSentenceAnnotator(CommandLine cmd) throws
      ResourceInitializationException {
    SentenceAnnotator.Builder b = SentenceAnnotator.configure();
    if (cmd.hasOption('s')) b.splitOnSingleNewlines();
    else if (cmd.hasOption('S')) b.splitIgnoringNewlines();
    else b.splitOnSuccessiveNewlines();
    if (cmd.hasOption("sentencemodel"))
      b.setModelResourceUrl("file:" + cmd.getOptionValue("sentencemodel"));
    return b.create();
  }

  /**
   * Get a configured JDBC connection resource descriptor.
   *
   * @param cmd                parsed command line options
   * @param l                  pipeline's the logger
   * @param defaultDriverClass the default driver class to use if not given
   * @param defaultProvider    the default DB provider (for that driver class) if not given
   * @param defaultDbName      the default DB (name) to connect to if not given
   *
   * @return the descriptor instance
   *
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws ResourceInitializationException
   *
   */
  public static
  ExternalResourceDescription getJdbcConnectionResource(CommandLine cmd, Logger l,
                                                        String defaultDriverClass,
                                                        String defaultProvider,
                                                        String defaultDbName) throws
      ResourceInitializationException, ClassNotFoundException {
    final String driverClass = getJdbcDriver(cmd, defaultDriverClass);
    final String dbUrl = getJdbcUrl(cmd, l, defaultProvider, defaultDbName);
    JdbcConnectionResourceImpl.Builder b = JdbcConnectionResourceImpl.configure(dbUrl, driverClass);
    configureAuthentication(cmd, b);
    return b.create();
  }

  /**
   * Configure DB authentication options on an authentication-enabled resource.
   *
   * @param cmd      parsed command line options
   * @param resource the resource to configure
   */
  public static
  void configureAuthentication(CommandLine cmd, AuthenticationResourceBuilder resource) {
    resource.setUsername(cmd.getOptionValue("dbpassword"));
    resource.setPassword(cmd.getOptionValue("dbuser"));
  }

  /**
   * Get and log a valid JDBC connection URL.
   *
   * @param cmd             parsed command line options
   * @param l               pipeline's the logger
   * @param defaultProvider the default DB provider (for a driver class) if none is given
   * @param defaultDbName   the default DB (name) to connect to if none is given
   *
   * @return the JDBC URL
   */
  public static
  String getJdbcUrl(CommandLine cmd, Logger l, String defaultProvider, String defaultDbName) {
    final String dbHost = cmd.getOptionValue('H', "localhost");
    final String dbProvider = cmd.getOptionValue("dbprovider", defaultProvider);
    final String dbName = cmd.getOptionValue('d', defaultDbName);
    String dbUrl = String.format("jdbc:%s://%s/%s", dbProvider, dbHost, dbName);
    l.log(Level.INFO, "JDBC URL: {0}", dbUrl);
    return dbUrl;
  }

  /**
   * Get and initialize a driver for JDBC connections.
   *
   * @param cmd                parsed command line options
   * @param defaultDriverClass the default driver class name to use if none is given
   *
   * @return the qualified driver class name that was initialized
   *
   * @throws ClassNotFoundException
   */
  public static
  String getJdbcDriver(CommandLine cmd, String defaultDriverClass) throws ClassNotFoundException {
    final String driverClass = cmd.getOptionValue("dbdriver", defaultDriverClass);
    Class.forName(driverClass);
    return driverClass;
  }

  /**
   * Get the right {@link Pipeline.XmlHandler} given the option value. Calls {@link
   * System#exit(int)} if an unknown handler was set.
   *
   * @param cmd command line options
   *
   * @return the correct XmlHandler given the options
   */
  public static
  XmlHandler getTikaXmlHandler(CommandLine cmd) {
    final String handler = cmd.getOptionValue('x', "default");
    if ("default".equals(handler)) return XmlHandler.DEFAULT;
    else if ("clean".equals(handler)) return XmlHandler.CLEAN;
    else if ("elsevier".equals(handler)) return XmlHandler.ELSEVIER;
    else if ("pmc".equals(handler)) return XmlHandler.PMC;
    // else: unknown handler
    System.err.print("no such XML handler: ");
    System.err.println(handler);
    throw new IllegalArgumentException("illegal XML handler choice " + handler);
  }

  /** Create an AE from a descriptor that uses CASes with multiple views. */
  public static
  AnalysisEngine multiviewEngine(AnalysisEngineDescription aed) throws
      ResourceInitializationException {
    return AnalysisEngineFactory.createPrimitive(aed);
  }

  /** Create an AE from a descriptor that uses CASes with a single (raw) view. */
  public static
  AnalysisEngine rawEngine(AnalysisEngineDescription aed) throws ResourceInitializationException {
    return AnalysisEngineFactory.createAnalysisEngine(aed, Views.CONTENT_RAW.toString());
  }

  /** Create an AE from a descriptor that uses CASes with a single (text) view. */
  public static
  AnalysisEngine textEngine(AnalysisEngineDescription aed) throws ResourceInitializationException {
    return AnalysisEngineFactory.createAnalysisEngine(aed, Views.CONTENT_TEXT.toString());
  }

  /**
   * Configure a pipeline with a given reader and the number of engines it will provide (without
   * counting the CAS consumer). The actual engines must be set at a later stage.
   *
   * @param reader     a collection reader configuration
   * @param numEngines number of analysis engines the pipeline will contain of without counting the
   *                   "last" engine that should be the CAS consumer
   */
  public
  Pipeline(CollectionReader reader, int numEngines) {
    collectionReader = reader;
    if (numEngines < 0) throw new IllegalArgumentException("numEngines=" + numEngines);
    pipeline = new AnalysisEngine[numEngines + 1];
  }

  public
  Pipeline(int numEngines, CollectionReaderDescription desc) throws
      ResourceInitializationException {
    this(CollectionReaderFactory.createCollectionReader(desc), numEngines);
  }

  /**
   * Configure a pipeline with the number of engines it will provide (without counting the CAS
   * consumer). The collection reader and actual engines must be set at a later stage.
   *
   * @param numEngines number of analysis engines the pipeline will contain of without counting the
   *                   "last" engine that should be the CAS consumer
   */
  public
  Pipeline(int numEngines) {
    this(null, numEngines);
  }

  /**
   * Configure a pipeline for a collection reader, one analysis engine, and a CAS consumer. The
   * collection reader, engine, and CAS consumer must be set at a later stage.
   */
  public
  Pipeline() {
    this(null, 1);
  }

  /**
   * Configure a pipeline for a collection reader, one analysis engine, and a CAS consumer. All but
   * the collection reader - and including the size of the pipeline - must be set at a later stage.
   *
   * @param reader a configured collection reader
   */
  public
  Pipeline(CollectionReader reader) {
    this(reader, 1);
  }

  /**
   * Configure a pipeline for a collection reader, one analysis engine, and a CAS consumer. All but
   * the collection reader - and including the size of the pipeline - must be set at a later stage.
   *
   * @param desc a collection reader configuration description
   */
  public
  Pipeline(CollectionReaderDescription desc) throws ResourceInitializationException {
    this(CollectionReaderFactory.createCollectionReader(desc));
  }

  /**
   * Configure a pipeline with a reader and analysis engine descriptions (including the CAS
   * consumer).
   *
   * @param reader  a collection reader configuration
   * @param engines the AE descriptions, <i>including the final CAS consumer</i>
   */
  public
  Pipeline(CollectionReader reader, AnalysisEngine... engines) {
    collectionReader = reader;
    set(engines);
  }

  /**
   * Configure a pipeline with analysis engine descriptions (including the CAS consumer). The
   * collection reader must be set at a later stage.
   *
   * @param engines the AE descriptions, <i>including the final CAS consumer</i>
   */
  public
  Pipeline(AnalysisEngine... engines) {
    this(null, engines);
  }

  // COLLECTION READER METHODS

  /** Get the currently configured reader for this pipeline. */
  public
  CollectionReader getReader() {
    return collectionReader;
  }

  /**
   * Set the collection reader for this pipeline.
   *
   * @throws ResourceInitializationException
   *
   */
  public
  CollectionReader setReader(CollectionReader reader) throws ResourceInitializationException {
    final CollectionReader last = collectionReader;
    collectionReader = reader;
    return last;
  }

  /**
   * Set the collection reader for this pipeline via its descriptor and configuration parameters.
   *
   * @throws ResourceInitializationException
   *
   */
  public
  CollectionReader setReader(CollectionReaderDescription desc, Object... configurationData) throws
      ResourceInitializationException {
    return setReader(CollectionReaderFactory.createCollectionReader(desc, configurationData));
  }

  /**
   * Set up a collection reader for this pipeline according to the given command line options.
   *
   * @param cmd the given command line options
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   */
  public
  CollectionReader setReader(CommandLine cmd) throws IOException, UIMAException {
    final String[] inputFiles = cmd.getArgs();
    final boolean recursive = cmd.hasOption('R');
    final String mimeType = cmd.getOptionValue("mime");
    File inputDirectory = null;
    // check input path arguments
    if (inputFiles.length > 0) {
      if (inputFiles.length == 1) {
        // a single argument can be a file or a directory
        inputDirectory = new File(inputFiles[0]);
        if (inputDirectory.isFile() && inputDirectory.canRead()) {
          inputDirectory = null;
        } else if (!(inputDirectory.isDirectory() && inputDirectory.canRead())) {
          throwNotReadable(inputFiles[0]);
        }
      } else {
        // multiple arguments have to be all files
        for (final String fn : inputFiles) {
          final File tmp = new File(fn);
          if (!tmp.canRead() || !tmp.isFile()) {
            throwNotReadable(fn);
          }
        }
      }
    } else {
      // if no arguments were given, use the current directory as input
      inputDirectory = new File(System.getProperty("user.dir"));
    }
    if (inputDirectory == null) return setReader(inputFiles, mimeType); // directory reader
    else return setReader(inputDirectory, mimeType, recursive); // file reader
  }

  /** Helper method to throw similar errors when a file or directory path cannot be read. */
  protected
  void throwNotReadable(final String path) throws IOException {
    final String msg = String.format("cannot read '%s'", path);
    System.err.println(msg);
    throw new IOException(msg);
  }

  /**
   * Set up a file collection reader for this pipeline.
   *
   * @param inputFiles the input file (paths) to read
   * @param mimeType   the MIME type of the input files
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   */
  public
  CollectionReader setReader(String[] inputFiles, String mimeType) throws IOException,
      UIMAException {
    return setReader(FileReader.configure(inputFiles).setMimeType(mimeType).create());
  }

  /**
   * Set up a file collection reader for this pipeline. Detects the MIME type using Tika.
   *
   * @param inputFiles the input file (paths) to read
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   * @see Pipeline#setReader(String[], String)
   */
  public
  CollectionReader setReader(String[] inputFiles) throws IOException, UIMAException {
    return setReader(FileReader.configure(inputFiles).create());
  }

  /**
   * Set up a directory collection reader for this pipeline.
   *
   * @param inputDirectory where the input files are located
   * @param mimeType       the MIMIE type of the input files
   * @param recursive      recurse into sub-directories
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   */
  public
  CollectionReader setReader(File inputDirectory, String mimeType, boolean recursive) throws
      IOException, UIMAException {
    DirectoryReader.Builder b = DirectoryReader.configure(inputDirectory).setMimeType(mimeType);
    if (recursive) b.recurseSubdirectories();
    return setReader(b.create());
  }

  /**
   * Set a <i>non-recursive</i> directory collection reader for this pipeline.
   *
   * @param inputDirectory where the input files are located
   * @param mimeType       the MIMIE type of the input files
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   * @see Pipeline#setReader(File, String, boolean)
   */
  public
  CollectionReader setReader(File inputDirectory, String mimeType) throws IOException,
      UIMAException {
    return setReader(DirectoryReader.configure(inputDirectory).setMimeType(mimeType).create());
  }

  /**
   * Set a directory collection reader for this pipeline. Detects the MIME type using Tika.
   *
   * @param inputDirectory where the input files are located
   * @param recursive      recurse into sub-directories
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   * @see Pipeline#setReader(File, String, boolean)
   */
  public
  CollectionReader setReader(File inputDirectory, boolean recursive) throws IOException,
      UIMAException {
    DirectoryReader.Builder b = DirectoryReader.configure(inputDirectory);
    if (recursive) b.recurseSubdirectories();
    return setReader(b.create());
  }

  /**
   * Set a <i>non-recursive</i> directory collection reader for this pipeline. Detects the MIME type
   * using Tika.
   *
   * @param inputDirectory where the input files are located
   *
   * @return a formerly configured collection reader (if any)
   *
   * @throws IOException
   * @throws UIMAException
   * @see Pipeline#setReader(File, String, boolean)
   */
  public
  CollectionReader setReader(File inputDirectory) throws IOException, UIMAException {
    return setReader(DirectoryReader.configure(inputDirectory).create());
  }

  // CAS CONSUMER METHODS

  /**
   * Sets the last engine, which should be a CAS consumer.
   *
   * @return a formerly configured AE/CAS consumer (if any)
   */
  public
  AnalysisEngine setConsumer(AnalysisEngine consumer) {
    final AnalysisEngine before = pipeline[size()];
    pipeline[size()] = consumer;
    return before;
  }

  // TIKA EXTRACTION
  public
  enum XmlHandler {
    DEFAULT, CLEAN, PMC, ELSEVIER
  }

  /**
   * Configure the Tika extraction system for the pipeline.
   * <p/>
   * Note that as a pipeline must cover at least two analysis engines (one or more AEs and a CAS
   * consumer), the <code>idx</code> position should not be the last position in the pipeline (which
   * should be reserved for a CAS consumer).
   *
   * @param idx            the position in the pipeline the AE should have (where 0 is the first AE
   *                       after the reader)
   * @param simple         if only text extraction, but not structural annotations ( {@link
   *                       txtfnnl.uima.tcas.StructureAnnotation}) should be done
   * @param encoding       optional character encoding to use (otherwise the platform default is
   *                       used); may be <code>null</code>
   * @param normalizeGreek if <code>true</code>, Greek characters (including the letter "sharp S"
   *                       (U+00DF) - that will be treated as "beta") will be normzalized to
   *                       spelled-out ASCII (i.e., using Latin letters).
   * @param handler        to use for XML files (MIME types)
   *
   * @return the originally set AE
   *
   * @throws IOException
   * @throws UIMAException
   */
  public
  AnalysisEngine configureTika(int idx, boolean simple, String encoding, boolean normalizeGreek,
                               XmlHandler handler) throws UIMAException, IOException {
    if (pipeline.length < 2)
      throw new IllegalStateException("trying to configure a Tika AE on a pipeline of length 1");
    if (idx + 1 == pipeline.length) throw new IllegalStateException(
        "trying to configure a Tike AE as last element of a pipeline"
    );
    AbstractTikaAnnotator.Builder tikaConfig =
        (simple) ? TikaExtractor.configure() : TikaAnnotator.configure();
    switch (handler) {
    case CLEAN:
      tikaConfig.setXmlHandlerClass(CleanBodyContentHandler.class);
      break;
    case ELSEVIER:
      tikaConfig.setXmlHandlerClass(ElsevierXMLContentHandler.class);
      break;
    case PMC:
      tikaConfig.setXmlHandlerClass(PubMedCentralXMLContentHandler.class);
      break;
    case DEFAULT:
    default:
      tikaConfig.setXmlHandlerClass(XMLContentHandler.class);
    }
    tikaConfig.setEncoding(encoding);
    if (normalizeGreek) tikaConfig.normalizeGreek();
    return set(idx, AnalysisEngineFactory.createPrimitive(tikaConfig.create()));
  }

  /**
   * Configure the Tika extraction system for the pipeline in the first position after the reader (
   * <code>idx=0</code>).
   *
   * @see Pipeline#configureTika(int, boolean, String, boolean, XmlHandler)
   */
  public
  AnalysisEngine configureTika(boolean simple, String encoding, boolean normalizeGreek,
                               XmlHandler handler) throws UIMAException, IOException {
    return configureTika(0, simple, encoding, normalizeGreek, handler);
  }

  /**
   * Configure the Tika extraction system for the pipeline using the command line options.
   * <p/>
   * Note that as a pipeline must cover at least two analysis engines (one or more AEs and a CAS
   * consumer), the <code>idx</code> position should not be the last position in the pipeline (which
   * should be reserved for a CAS consumer).
   *
   * @see Pipeline#configureTika(int, boolean, String, boolean, XmlHandler)
   */
  public
  AnalysisEngine configureTika(int idx, boolean simple, CommandLine cmd) throws IOException,
      UIMAException {
    final XmlHandler handler = Pipeline.getTikaXmlHandler(cmd);
    final String encoding = cmd.getOptionValue('e');
    final boolean normalizeGreek = cmd.hasOption("normalgreek");
    return configureTika(idx, simple, encoding, normalizeGreek, handler);
  }

  /**
   * Configure the Tika extraction system for the pipeline in the first position after the reader (
   * <code>idx=0</code>).
   *
   * @see Pipeline#configureTika(int, boolean, String, boolean, XmlHandler)
   */
  public
  AnalysisEngine configureTika(boolean simple, CommandLine cmd) throws IOException, UIMAException {
    return configureTika(0, simple, cmd);
  }

  /**
   * Configure a simple Tika extraction system for the pipeline in the first position after the
   * reader (<code>idx=0</code>).
   *
   * @see Pipeline#configureTika(int, boolean, String, boolean, XmlHandler)
   */
  public
  AnalysisEngine configureTika(CommandLine cmd) throws IOException, UIMAException {
    return configureTika(true, cmd);
  }

  /**
   * Configure a simple Tika extraction system for the pipeline in the first position after the
   * reader (<code>idx=0</code>) using all defaults.
   *
   * @see Pipeline#configureTika(int, boolean, String, boolean, XmlHandler)
   */
  public
  AnalysisEngine configureTika() throws IOException, UIMAException {
    return configureTika(true, null, false, XmlHandler.DEFAULT);
  }

  // ANALYSIS ENGINE METHODS

  /**
   * Set the first Analysis Engine, returning the originally set AE. Consider that commonly this
   * position will be occupied by a Tika AE, however.
   */
  public
  AnalysisEngine setFirst(AnalysisEngine engine) {
    final AnalysisEngine originalEngine = pipeline[0];
    pipeline[0] = engine;
    return originalEngine;
  }

  /** Set a particular Analysis Engine, returning the originally set AE. */
  public
  AnalysisEngine set(int index, AnalysisEngine engine) {
    final AnalysisEngine originalEngine = pipeline[index];
    pipeline[index] = engine;
    return originalEngine;
  }

  /** Get a particular Analysis Engine. */
  public
  AnalysisEngine get(int index) {
    return pipeline[index];
  }

  /** Set all Analysis Engines, including the CAS Consumer, returning the original list of AEs. */
  public
  AnalysisEngine[] set(AnalysisEngine[] engines) {
    final AnalysisEngine[] before = pipeline;
    if (engines.length < 1) throw new IllegalArgumentException("empty engine description array");
    pipeline = engines;
    return before;
  }

  /**
   * Return the total number of (potentially not yet configured) analysis engines (without the CAS
   * consumer) this pipeline can hold.
   */
  public
  int size() {
    return pipeline.length - 1;
  }

  // GENERAL

  /**
   * <code>true</code> if all AEs, the CAS consumer, and the collection reader are set (i.e., are
   * not <code>null</code>).
   */
  public
  boolean isReady() {
    if (collectionReader == null) return false;
    for (final AnalysisEngine engine : pipeline)
      if (engine == null) return false;
    return true;
  }

  /** Run the pipeline. */
  public
  void run() throws UIMAException, IOException {
    SimplePipeline.runPipeline(collectionReader, pipeline);
  }

  /** Destroy the pipeline. */
  public
  void destroy() {
    collectionReader.destroy();
    for (AnalysisEngine ae : pipeline)
      ae.destroy();
    collectionReader = null;
    pipeline = new AnalysisEngine[pipeline.length];
  }
}
