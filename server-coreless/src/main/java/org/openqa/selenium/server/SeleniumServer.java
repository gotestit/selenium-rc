/*
 * Copyright 2006 ThoughtWorks, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.openqa.selenium.server;

import org.apache.commons.logging.Log;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.HttpContext;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.SecurityHandler;
import org.mortbay.jetty.Server;
import org.mortbay.log.LogFactory;
import org.openqa.selenium.server.BrowserSessionFactory.BrowserSessionInfo;
import org.openqa.selenium.server.browserlaunchers.AsyncExecute;
import org.openqa.selenium.server.cli.RemoteControlLauncher;
import org.openqa.selenium.server.htmlrunner.HTMLLauncher;
import org.openqa.selenium.server.htmlrunner.HTMLResultsListener;
import org.openqa.selenium.server.htmlrunner.SeleniumHTMLRunnerResultsHandler;
import org.openqa.selenium.server.htmlrunner.SingleTestSuiteResourceHandler;
import org.openqa.selenium.server.log.MaxLevelFilter;
import org.openqa.selenium.server.log.StdOutHandler;
import org.openqa.selenium.server.log.TerseFormatter;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.*;

/**
 * Provides a server that can launch/terminate browsers and can receive remote Selenium commands
 * over HTTP and send them on to the browser.
 * <p/>
 * <p>To run Selenium Server, run:
 * <p/>
 * <blockquote><code>java -jar selenium-server-1.0-SNAPSHOT.jar [-port 4444] [-interactive] [-timeout 1800]</code></blockquote>
 * <p/>
 * <p>Where <code>-port</code> specifies the port you wish to run the Server on (default is 4444).
 * <p/>
 * <p>Where <code>-timeout</code> specifies the number of seconds that you allow data to wait all in the
 * communications queues before an exception is thrown.
 * <p/>
 * <p>Using the <code>-interactive</code> flag will start the server in Interactive mode.
 * In this mode you can type remote Selenium commands on the command line (e.g. cmd=open&1=http://www.yahoo.com).
 * You may also interactively specify commands to run on a particular "browser session" (see below) like this:
 * <blockquote><code>cmd=open&1=http://www.yahoo.com&sessionId=1234</code></blockquote></p>
 * <p/>
 * <p>The server accepts three types of HTTP requests on its port:
 * <p/>
 * <ol>
 * <li><b>Client-Configured Proxy Requests</b>: By configuring your browser to use the
 * Selenium Server as an HTTP proxy, you can use the Selenium Server as a web proxy.  This allows
 * the server to create a virtual "/selenium-server" directory on every website that you visit using
 * the proxy.
 * <li><b>Remote Browser Commands</b>: If the browser goes to "/selenium-server/RemoteRunner.html?sessionId=1234" on any website
 * via the Client-Configured Proxy, it will ask the Selenium Server for work to do, like this:
 * <blockquote><code>http://www.yahoo.com/selenium-server/driver/?seleniumStart=true&sessionId=1234</code></blockquote>
 * The driver will then reply with a command to run in the body of the HTTP response, e.g. "|open|http://www.yahoo.com||".  Once
 * the browser is done with this request, the browser will issue a new request for more work, this
 * time reporting the results of the previous command:<blockquote><code>http://www.yahoo.com/selenium-server/driver/?commandResult=OK&sessionId=1234</code></blockquote>
 * The action list is listed in selenium-api.js.  Normal actions like "doClick" will return "OK" if
 * clicking was successful, or some other error string if there was an error.  Assertions like
 * assertTextPresent or verifyTextPresent will return "PASSED" if the assertion was true, or
 * some other error string if the assertion was false.  Getters like "getEval" will return the
 * result of the get command.  "getAllLinks" will return a comma-delimited list of links.</li>
 * <li><b>Driver Commands</b>: Clients may send commands to the Selenium Server over HTTP.
 * Command requests should look like this:<blockquote><code>http://localhost:4444/selenium-server/driver/?commandRequest=|open|http://www.yahoo.com||&sessionId=1234</code></blockquote>
 * The Selenium Server will not respond to the HTTP request until the browser has finished performing the requested
 * command; when it does, it will reply with the result of the command (e.g. "OK" or "PASSED") in the
 * body of the HTTP response.  (Note that <code>-interactive</code> mode also works by sending these
 * HTTP requests, so tests using <code>-interactive</code> mode will behave exactly like an external client driver.)
 * </ol>
 * <p>There are some special commands that only work in the Selenium Server.  These commands are:
 * <ul><li><p><strong>getNewBrowserSession</strong>( <em>browserString</em>, <em>startURL</em> )</p>
 * <p>Creates a new "sessionId" number (based on the current time in milliseconds) and launches the browser specified in
 * <i>browserString</i>.  We will then browse directly to <i>startURL</i> + "/selenium-server/RemoteRunner.html?sessionId=###"
 * where "###" is the sessionId number. Only commands that are associated with the specified sessionId will be run by this browser.</p>
 * <p/>
 * <p><i>browserString</i> may be any one of the following:
 * <ul>
 * <li><code>*firefox [absolute path]</code> - Automatically launch a new Firefox process using a custom Firefox profile.
 * This profile will be automatically configured to use the Selenium Server as a proxy and to have all annoying prompts
 * ("save your password?" "forms are insecure" "make Firefox your default browser?" disabled.  You may optionally specify
 * an absolute path to your firefox executable, or just say "*firefox".  If no absolute path is specified, we'll look for
 * firefox.exe in a default location (normally c:\program files\mozilla firefox\firefox.exe), which you can override by
 * setting the Java system property <code>firefoxDefaultPath</code> to the correct path to Firefox.</li>
 * <li><code>*iexplore [absolute path]</code> - Automatically launch a new Internet Explorer process using custom Windows registry settings.
 * This process will be automatically configured to use the Selenium Server as a proxy and to have all annoying prompts
 * ("save your password?" "forms are insecure" "make Firefox your default browser?" disabled.  You may optionally specify
 * an absolute path to your iexplore executable, or just say "*iexplore".  If no absolute path is specified, we'll look for
 * iexplore.exe in a default location (normally c:\program files\internet explorer\iexplore.exe), which you can override by
 * setting the Java system property <code>iexploreDefaultPath</code> to the correct path to Internet Explorer.</li>
 * <li><code>/path/to/my/browser [other arguments]</code> - You may also simply specify the absolute path to your browser
 * executable, or use a relative path to your executable (which we'll try to find on your path).  <b>Warning:</b> If you
 * specify your own custom browser, it's up to you to configure it correctly.  At a minimum, you'll need to configure your
 * browser to use the Selenium Server as a proxy, and disable all browser-specific prompting.
 * </ul>
 * </li>
 * <li><p><strong>testComplete</strong>(  )</p>
 * <p>Kills the currently running browser and erases the old browser session.  If the current browser session was not
 * launched using <code>getNewBrowserSession</code>, or if that session number doesn't exist in the server, this
 * command will return an error.</p>
 * </li>
 * <li><p><strong>shutDown</strong>(  )</p>
 * <p>Causes the server to shut itself down, killing itself and all running browsers along with it.</p>
 * </li>
 * </ul>
 * <p>Example:<blockquote><code>cmd=getNewBrowserSession&1=*firefox&2=http://www.google.com
 * <br/>Got result: 1140738083345
 * <br/>cmd=open&1=http://www.google.com&sessionId=1140738083345
 * <br/>Got result: OK
 * <br/>cmd=type&1=q&2=hello world&sessionId=1140738083345
 * <br/>Got result: OK
 * <br/>cmd=testComplete&sessionId=1140738083345
 * <br/>Got result: OK
 * </code></blockquote></p>
 * <p/>
 * <h4>The "null" session</h4>
 * <p/>
 * <p>If you open a browser manually and do not specify a session ID, it will look for
 * commands using the "null" session.  You may then similarly send commands to this
 * browser by not specifying a sessionId when issuing commands.</p>
 *
 * @author plightbo
 */
public class SeleniumServer {
    private static Log logger;

    private Server server;
    private SeleniumDriverResourceHandler driver;
    private SeleniumHTMLRunnerResultsHandler postResultsHandler;
    private StaticContentHandler staticContentHandler;
    private final RemoteControlConfiguration configuration;
    private Thread shutDownHook;

    private static ProxyHandler customProxyHandler;
    private static boolean browserSideLogEnabled = false;
    private static boolean avoidProxy = false;
    private static boolean proxyInjectionMode = false;
    private static boolean ensureCleanSession = false;
    private static Handler[] defaultHandlers;
    private static Map<Handler, Formatter> defaultFormatters;
    private static Map<Handler, Level> defaultLevels;
    private static Map<File, FileHandler> seleniumFileHandlers = new HashMap<File, FileHandler>();

    public static final int DEFAULT_TIMEOUT = (30 * 60);
    public static int timeoutInSeconds = DEFAULT_TIMEOUT;
    public static int retryTimeoutInSeconds = 10;

    // Minimum and maximum number of jetty threads
    public static final int MIN_JETTY_THREADS = 1;
    public static final int MAX_JETTY_THREADS = 1024;
    public static final int DEFAULT_JETTY_THREADS = 512;

    // Number of jetty threads for the server
    private static int jettyThreads = DEFAULT_JETTY_THREADS;


    private static boolean FORCE_PROXY_CHAIN = false;
    private static boolean debugMode = false;

    // useful for situations where Selenium is being invoked programatically and the outside container wants to own logging
    private static boolean dontTouchLogging = false;

    private static final int MAX_SHUTDOWN_RETRIES = 8;

    /**
     * Starts up the server on the specified port (or default if no port was specified)
     * and then starts interactive mode if specified.
     *
     * @param args - either "-port" followed by a number, or "-interactive"
     * @throws Exception - you know, just in case.
     */
    public static void main(String[] args) throws Exception {
        final RemoteControlConfiguration configuration;
        final SeleniumServer seleniumProxy;

        configuration = RemoteControlLauncher.parseLauncherOptions(args);
        checkArgsSanity(configuration);

        System.setProperty("org.mortbay.http.HttpRequest.maxFormContentSize", "0"); // default max is 200k; zero is infinite
        seleniumProxy = new SeleniumServer(slowResourceProperty(), configuration);
        seleniumProxy.boot();
    }


    public SeleniumServer() throws Exception {
        this(slowResourceProperty(), new RemoteControlConfiguration());
    }

    public SeleniumServer(boolean slowResources) throws Exception {
        this(slowResources, new RemoteControlConfiguration());
    }

    /**
     * Prepares a Jetty server with its HTTP handlers.
     *
     * @param slowResources should the webserver return static resources more slowly?  (Note that this will not slow down ordinary RC test runs; this setting is used to debug Selenese HTML tests.)
     * @param configuration  Remote Control configuration. Cannot be null.
     * @throws Exception you know, just in case
     */
    public SeleniumServer(boolean slowResources, RemoteControlConfiguration configuration) throws Exception {
        this.configuration = configuration;
        configureLogging(configuration);
        logStartupInfo();
        String proxyHost = System.getProperty("http.proxyHost");
        String proxyPort = System.getProperty("http.proxyPort");
        if (Integer.toString(getPort()).equals(proxyPort)) {
            logger.debug("http.proxyPort is the same as the Selenium Server port " + getPort());
            logger.debug("http.proxyHost=" + proxyHost);
            if ("localhost".equals(proxyHost) || "127.0.0.1".equals(proxyHost)) {
                logger.info("Forcing http.proxyHost to '' to avoid infinite loop");
                System.setProperty("http.proxyHost", "");
            }
        }
        server = new Server();

        SocketListener socketListener = new SocketListener();
        socketListener.setMaxIdleTimeMs(60000);
        socketListener.setMaxThreads(jettyThreads);
        socketListener.setPort(getPort());
        server.addListener(socketListener);
        configServer();
        assembleHandlers(slowResources, configuration);
    }

    private void logStartupInfo() throws IOException {
        logger.info("Java: " + System.getProperty("java.vm.vendor") + ' ' + System.getProperty("java.vm.version"));
        logger.info("OS: " + System.getProperty("os.name") + ' ' + System.getProperty("os.version") + ' ' + System.getProperty("os.arch"));
        logVersionNumber();
        if (debugMode) {
            logger.info("Selenium server running in debug mode.");
        }
        if (proxyInjectionMode) {
            logger.info("The selenium server will execute in proxyInjection mode.");
        }
        if (configuration.reuseBrowserSessions()) {
            logger.info("Will recycle browser sessions when possible.");
        }
        if (null != configuration.getForcedBrowserMode()) {
            logger.info("\"" + configuration.getForcedBrowserMode() + "\" will be used as the browser " +
                    "mode for all sessions, no matter what is passed to getNewBrowserSession.");
        }
    }


    public void boot() throws Exception {
        start();
        if (null != configuration.getUserExtensions()) {
            addNewStaticContent(configuration.getUserExtensions().getParentFile());
        }

        if (configuration.isSelfTest()) {
            System.exit(runSelfTests() ? 0 : 1);
        }

        if (configuration.isHTMLSuite()) {
            runHtmlSuite();
            return;
        }

        if (configuration.isInteractive()) {
            readUserCommands();
        }
    }


    public static synchronized void configureLogging(RemoteControlConfiguration configuration) {
        if (dontTouchLogging) {
            logger = LogFactory.getLog(SeleniumServer.class);
            return;
        }

        Logger logger = Logger.getLogger("");
        resetLogger();
        // configure console logger
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                Formatter formatter = handler.getFormatter();
                if (formatter instanceof SimpleFormatter) {
                    /* DGF Nobody likes the SimpleFormatter; surely they
                     * wanted our terse formatter instead.
                     * Furthermore, we all want DEBUG/INFO on stdout and WARN/ERROR on stderr */
                    Level originalLevel = handler.getLevel();
                    handler.setFormatter(new TerseFormatter(false));
                    handler.setLevel(Level.WARNING);
                    StdOutHandler stdOutHandler = new StdOutHandler();
                    stdOutHandler.setFormatter(new TerseFormatter(false));
                    stdOutHandler.setFilter(new MaxLevelFilter(Level.INFO));
                    stdOutHandler.setLevel(originalLevel);
                    logger.addHandler(stdOutHandler);
                    if (isDebugMode()) {
                        if (originalLevel.intValue() > Level.FINE.intValue()) {
                            stdOutHandler.setLevel(Level.FINE);
                        }
                    }
                }
            }
        }

        if (isDebugMode()) {
            logger.setLevel(Level.FINE);
        }

        SeleniumServer.logger = LogFactory.getLog(SeleniumServer.class);
        if (null == configuration.getLogOutFileName() && System.getProperty("selenium.log") != null) {
            configuration.setLogOutFileName(System.getProperty("selenium.log"));
        }
        if (null != configuration.getLogOutFile()) {
            try {
                File logFile = configuration.getLogOutFile();
                FileHandler fileHandler = seleniumFileHandlers.get(logFile);
                if (fileHandler == null) {
                    fileHandler = new FileHandler(logFile.getAbsolutePath());
                    seleniumFileHandlers.put(logFile, fileHandler);
                }
                fileHandler.setFormatter(new TerseFormatter(true));
                logger.setLevel(Level.FINE);
                fileHandler.setLevel(Level.FINE);
                logger.addHandler(fileHandler);
                SeleniumServer.logger.info("Writing debug logs to " + logFile.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void logVersionNumber() throws IOException {
        InputStream stream = SeleniumServer.class.getResourceAsStream("/VERSION.txt");
        if (stream == null) {
            logger.error("Couldn't determine version number");
            return;
        }
//    	BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
//    	StringBuffer sb = new StringBuffer();
//        String line;
//        while ((line = reader.readLine()) != null) {
//            sb.append(line);
//        }
//        String pac = sb.toString();
        Properties p = new Properties();
        p.load(stream);
        String rcVersion = p.getProperty("selenium.rc.version");
        String rcRevision = p.getProperty("selenium.rc.revision");
        String coreVersion = p.getProperty("selenium.core.version");
        String coreRevision = p.getProperty("selenium.core.revision");
        logger.info("v" + rcVersion + " [" + rcRevision + "], with Core v" + coreVersion + " [" + coreRevision + "]");
    }

    private static void resetLogger() {
        Logger logger = Logger.getLogger("");
        if (defaultHandlers == null) {
            defaultHandlers = logger.getHandlers();
            defaultFormatters = new HashMap<Handler, Formatter>();
            defaultLevels = new HashMap<Handler, Level>();
            for (Handler handler : defaultHandlers) {
                defaultFormatters.put(handler, handler.getFormatter());
                defaultLevels.put(handler, handler.getLevel());
            }
        } else {
            for (Handler handler : logger.getHandlers()) {
                logger.removeHandler(handler);
            }
            for (Handler handler : defaultHandlers) {
                logger.addHandler(handler);
                handler.setFormatter(defaultFormatters.get(handler));
                handler.setLevel(defaultLevels.get(handler));
            }
        }

    }

    private void assembleHandlers(boolean slowResources, RemoteControlConfiguration configuration) {
        HttpContext root = new HttpContext();
        root.setContextPath("/");

        ProxyHandler proxyHandler;
        if (customProxyHandler == null) {
            proxyHandler = new ProxyHandler(configuration.trustAllSSLCertificates(), configuration.getDontInjectRegex(), configuration.getDebugURL());
            root.addHandler(proxyHandler);
        } else {
            proxyHandler = customProxyHandler;
            root.addHandler(proxyHandler);
        }
        // pre-compute the 1-16 SSL relays+certs for the logging hosts (see selenium-remoterunner.js sendToRCAndForget for more info)
        if (browserSideLogEnabled) {
            proxyHandler.generateSSLCertsForLoggingHosts(server);
        }

        server.addContext(root);

        HttpContext context = new HttpContext();
        context.setContextPath("/selenium-server");
        context.setMimeMapping("xhtml", "application/xhtml+xml");

        SecurityConstraint constraint = new SecurityConstraint();
        constraint.setName(SecurityConstraint.__BASIC_AUTH);

        constraint.addRole("user");
        constraint.setAuthenticate(true);

        context.addSecurityConstraint("/tests/html/basicAuth/*", constraint);
        HashUserRealm realm = new HashUserRealm("MyRealm");
        realm.put("alice", "foo");
        realm.addUserToRole("alice", "user");
        context.setRealm(realm);

        SecurityHandler sh = new SecurityHandler();
        context.addHandler(sh);

        StaticContentHandler.setSlowResources(slowResources);
        staticContentHandler = new StaticContentHandler(configuration.getDebugURL());
        String overrideJavascriptDir = System.getProperty("selenium.javascript.dir");
        if (overrideJavascriptDir != null) {
            staticContentHandler.addStaticContent(new FsResourceLocator(new File(overrideJavascriptDir)));
        }
        staticContentHandler.addStaticContent(new ClasspathResourceLocator());

        context.addHandler(staticContentHandler);
        context.addHandler(new SingleTestSuiteResourceHandler());

        postResultsHandler = new SeleniumHTMLRunnerResultsHandler();
        context.addHandler(postResultsHandler);

        CachedContentTestHandler cachedContentTestHandler = new CachedContentTestHandler();
        context.addHandler(cachedContentTestHandler);

        // Associate the SeleniumDriverResourceHandler with the /selenium-server/driver context
        HttpContext driverContext = new HttpContext();
        driverContext.setContextPath("/selenium-server/driver");
        driver = new SeleniumDriverResourceHandler(this);
        context.addHandler(driver);

        server.addContext(context);
        server.addContext(driverContext);
    }

    private void configServer() {
        if (null == configuration.getForcedBrowserMode()) {
            if (null != System.getProperty("selenium.defaultBrowserString")) {
                System.err.println("The selenium.defaultBrowserString property is no longer supported; use selenium.forcedBrowserMode instead.");
                System.exit(-1);
            }
            configuration.setForcedBrowserMode(System.getProperty("selenium.forcedBrowserMode"));
        }
        if (!isProxyInjectionMode() && System.getProperty("selenium.proxyInjectionMode") != null) {
            setProxyInjectionMode("true".equals(System.getProperty("selenium.proxyInjectionMode")));
        }
        if (!isDebugMode() && System.getProperty("selenium.debugMode") != null) {
            setDebugMode("true".equals(System.getProperty("selenium.debugMode")));
        }
        if (!isBrowserSideLogEnabled() && System.getProperty("selenium.browserSideLog") != null) {
            setBrowserSideLogEnabled("true".equals(System.getProperty("selenium.browserSideLog")));
        }
    }


    public static boolean isEnsureCleanSession() {
        return ensureCleanSession;
    }

    public static void setEnsureCleanSession(boolean value) {
        ensureCleanSession = value;
    }

    private static boolean slowResourceProperty() {
        return ("true".equals(System.getProperty("slowResources")));
    }

    public static void setTimeoutInSeconds(int timeoutInSeconds) {
        SeleniumServer.timeoutInSeconds = timeoutInSeconds;
    }

    public static void setRetryTimeoutInSeconds(int timeoutInSeconds) {
        SeleniumServer.retryTimeoutInSeconds = timeoutInSeconds;
    }

    public void addNewStaticContent(File directory) {
        staticContentHandler.addStaticContent(new FsResourceLocator(directory));
    }

    public void handleHTMLRunnerResults(HTMLResultsListener listener) {
        postResultsHandler.addListener(listener);
    }

    /**
     * Starts the Jetty server
     * 
     * @throws Exception on error.
     */
    public void start() throws Exception {
        System.setProperty("org.mortbay.http.HttpRequest.maxFormContentSize", "0"); // default max is 200k; zero is infinite
        server.start();

        shutDownHook = new Thread(new ShutDownHook(this));
        shutDownHook.setName("SeleniumServerShutDownHook");
        Runtime.getRuntime().addShutdownHook(shutDownHook);
    }

    public static boolean isForceProxyChain() {
        return FORCE_PROXY_CHAIN;
    }

    public static void setForceProxyChain(boolean force) {
        FORCE_PROXY_CHAIN = force;
    }

    /**
     * Used for implementations that invoke SeleniumServer programmatically and require additional logic
     * when proxying data.
     */
    public static void setCustomProxyHandler(ProxyHandler customProxyHandler) {
        SeleniumServer.customProxyHandler = customProxyHandler;
    }

    private class ShutDownHook implements Runnable {
        SeleniumServer selenium;

        ShutDownHook(SeleniumServer selenium) {
            this.selenium = selenium;
        }

        public void run() {
            logger.info("Shutting down...");
            selenium.stop();
        }
    }

    /**
     * Stops the Jetty server
     */
    public void stop() {
        int numTries = 0;
        Exception shutDownException = null;

        // this may be called by a shutdown hook, or it may be called at any time
        // in case it was called as an ordinary method, try to clean up the shutdown
        // hook
        try {
            if (shutDownHook != null) {
                Runtime.getRuntime().removeShutdownHook(shutDownHook);
            }
        } catch (IllegalStateException e) {
        } // thrown if we're shutting down; that's OK

        // shut down the jetty server (try try again)
        while (numTries <= MAX_SHUTDOWN_RETRIES) {
            ++numTries;
            try {
                server.stop();

                // If we reached here stop didnt throw an exception.
                // So we assume it was successful.
                break;
            } catch (Exception ex) { // org.mortbay.jetty.Server.stop() throws Exception
                logger.error(ex);
                shutDownException = ex;
                // If Exception is thrown we try to stop the jetty server again
            }
        }

        // next, stop all of the browser sessions.
        driver.stopAllBrowsers();

        if (numTries > MAX_SHUTDOWN_RETRIES) { // This is bad!! Jetty didnt shutdown..
            if (null != shutDownException) {
                throw new RuntimeException(shutDownException);
            }
        }
    }

    public RemoteControlConfiguration getConfiguration() {
        return configuration;
    }

    public int getPort() {
        return configuration.getPort();
    }

    /**
     * Exposes the internal Jetty server used by Selenium.
     * This lets users add their own webapp to the Selenium Server jetty instance.
     * It is also a minor violation of encapsulation principles (what if we stop
     * using Jetty?) but life is too short to worry about such things.
     *
     * @return the internal Jetty server, pre-configured with the /selenium-server context as well as
     *         the proxy server on /
     */
    public Server getServer() {
        return server;
    }

    public InputStream getResourceAsStream(String path) throws IOException {
        return staticContentHandler.getResource(path).getInputStream();
    }

    /**
     * Registers a running browser session
     */
    public void registerBrowserSession(BrowserSessionInfo sessionInfo) {
        driver.registerBrowserSession(sessionInfo);
    }

    /**
     * De-registers a previously registered running browser session
     */
    public void deregisterBrowserSession(BrowserSessionInfo sessionInfo) {
        driver.deregisterBrowserSession(sessionInfo);
    }

    /**
     * Get the number of threads that the server will use to configure the embedded Jetty instance.
     *
     * @return Returns the number of threads for Jetty.
     */
    public static int getJettyThreads() {
        return jettyThreads;
    }

    /**
     * Set the number of threads that the server will use for Jetty.
     * <p/>
     * In order to use this, you must call this method before you call the
     * SeleniumServer constructor.
     *
     * @param jettyThreads Number of jetty threads for the server to use
     * @throws IllegalArgumentException when jettyThreads < MIN_JETTY_THREADS or > MAX_JETTY_THREADS
     */
    public static void setJettyThreads(int jettyThreads) {
        if (jettyThreads < MIN_JETTY_THREADS
                || jettyThreads > MAX_JETTY_THREADS) {
            throw new IllegalArgumentException(
                    "Number of jetty threads specified as an argument must be greater than zero and less than "
                            + MAX_JETTY_THREADS);
        }

        SeleniumServer.jettyThreads = jettyThreads;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static boolean isBrowserSideLogEnabled() {
        return SeleniumServer.browserSideLogEnabled;
    }

    static public void setDebugMode(boolean debugMode) {
        SeleniumServer.debugMode = debugMode;
    }

    static public void setBrowserSideLogEnabled(boolean browserSideLogEnabled) {
        SeleniumServer.browserSideLogEnabled = browserSideLogEnabled;
    }

    public static boolean isProxyInjectionMode() {
        return proxyInjectionMode;
    }

    /**
     * By default, we proxy every browser request; set this flag to make the browser use our proxy only for URLs containing '/selenium-server'
     *
     * @param alwaysProxy true if we should always proxy, false otherwise
     * @deprecated use setAvoidProxy instead (note that avoidProxy is the opposite of alwaysProxy)
     */
    @Deprecated
    public static void setAlwaysProxy(boolean alwaysProxy) {
        setAvoidProxy(!alwaysProxy);
    }

    /**
     * By default, we proxy every browser request; set this flag to make the browser use our proxy only for URLs containing '/selenium-server'
     *
     * @param avoidProxy true if we should proxy as little as possible, false to proxy everything
     */
    public static void setAvoidProxy(boolean avoidProxy) {
        SeleniumServer.avoidProxy = avoidProxy;
    }

    /**
     * By default, we proxy every browser request; if this flag is set, we make the browser use our
     * proxy only for URLs containing '/selenium-server'
     *
     * @return Whether only URLs containing '/selenium-server' should be proxied.
     */
    public static boolean isAvoidProxy() {
        return avoidProxy;
    }

    public static void setProxyInjectionMode(boolean proxyInjectionMode) {
        SeleniumServer.proxyInjectionMode = proxyInjectionMode;
    }

    public static int getTimeoutInSeconds() {
        return timeoutInSeconds;
    }

    public static void setDontTouchLogging(boolean dontTouchLogging) {
        SeleniumServer.dontTouchLogging = dontTouchLogging;
    }

    private static String getRequiredSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null) {
            RemoteControlLauncher.usage("expected property " + name + " to be defined");
            System.exit(1);
        }
        return value;
    }

    private void runHtmlSuite() {
        final String result;
        try {
            String suiteFilePath = getRequiredSystemProperty("htmlSuite.suiteFilePath");
            File suiteFile = new File(suiteFilePath);
            if (!suiteFile.exists()) {
                RemoteControlLauncher.usage("Can't find HTML Suite file:" + suiteFile.getAbsolutePath());
                System.exit(1);
            }
            addNewStaticContent(suiteFile.getParentFile());
            String startURL = getRequiredSystemProperty("htmlSuite.startURL");
            HTMLLauncher launcher = new HTMLLauncher(this);
            String resultFilePath = getRequiredSystemProperty("htmlSuite.resultFilePath");
            File resultFile = new File(resultFilePath);
            resultFile.createNewFile();

            if (!resultFile.canWrite()) {
                RemoteControlLauncher.usage("can't write to result file " + resultFilePath);
                System.exit(1);
            }

            result = launcher.runHTMLSuite(getRequiredSystemProperty("htmlSuite.browserString"), startURL, suiteFile, resultFile,
                    timeoutInSeconds, configuration.isMultiWindow());

            if (!"PASSED".equals(result)) {
                System.err.println("Tests failed, see result file for details: " + resultFile.getAbsolutePath());
                System.exit(1);
            } else {
                System.exit(0);
            }
        } catch (Exception e) {
            System.err.println("HTML suite exception seen:");
            e.printStackTrace();
            System.exit(1);
        }

    }

    private void readUserCommands() throws IOException {
        AsyncExecute.sleepTight(500);
        System.out.println("Entering interactive mode... type Selenium commands here (e.g: cmd=open&1=http://www.yahoo.com)");
        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String userInput;

        final String[] lastSessionId = new String[]{""};

        while ((userInput = stdIn.readLine()) != null) {
            userInput = userInput.trim();
            if ("exit".equals(userInput) || "quit".equals(userInput)) {
                System.out.println("Stopping...");
                stop();
                System.exit(0);
            }

            if ("".equals(userInput)) continue;

            if (!userInput.startsWith("cmd=") && !userInput.startsWith("commandResult=")) {
                System.err.println("ERROR -  Invalid command: \"" + userInput + "\"");
                continue;
            }

            final boolean newBrowserSession = userInput.indexOf("getNewBrowserSession") != -1;
            if (userInput.indexOf("sessionId") == -1 && !newBrowserSession) {
                userInput = userInput + "&sessionId=" + lastSessionId[0];
            }

            final URL url = new URL("http://localhost:" + configuration.getPort() + "/selenium-server/driver?" + userInput);
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        logger.info("---> Requesting " + url.toString());
                        URLConnection conn = url.openConnection();
                        conn.connect();
                        InputStream is = conn.getInputStream();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buffer = new byte[2048];
                        int length;
                        while ((length = is.read(buffer)) != -1) {
                            out.write(buffer, 0, length);
                        }
                        is.close();

                        String output = out.toString();

                        if (newBrowserSession) {
                            if (output.startsWith("OK,")) {
                                lastSessionId[0] = output.substring(3);
                            }
                        }
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                        if (SeleniumServer.isDebugMode()) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            t.start();
        }
    }


    private boolean runSelfTests() throws IOException {
        return new HTMLLauncher(this).runSelfTests(configuration.getSelfTestDir());
    }

    private static void checkArgsSanity(RemoteControlConfiguration configuration) throws Exception {
        if (configuration.isInteractive()) {
            if (configuration.isHTMLSuite()) {
                System.err.println("You can't use -interactive and -htmlSuite on the same line!");
                System.exit(1);
            }
            if (configuration.isSelfTest()) {
                System.err.println("You can't use -interactive and -selfTest on the same line!");
                System.exit(1);
            }
        } else if (configuration.isSelfTest()) {
            if (configuration.isHTMLSuite()) {
                System.err.println("You can't use -selfTest and -htmlSuite on the same line!");
                System.exit(1);
            }
        }
        CommandQueue.setDefaultTimeout(timeoutInSeconds);
        CommandQueue.setRetryTimeout(retryTimeoutInSeconds);
        SeleniumServer.setProxyInjectionMode(configuration.getProxyInjectionModeArg());

        if (!isProxyInjectionMode() &&
                (InjectionHelper.userContentTransformationsExist() ||
                        InjectionHelper.userJsInjectionsExist())) {
            RemoteControlLauncher.usage("-userJsInjection and -userContentTransformation are only " +
                    "valid in combination with -proxyInjectionMode");
            System.exit(1);
        }
    }


}

