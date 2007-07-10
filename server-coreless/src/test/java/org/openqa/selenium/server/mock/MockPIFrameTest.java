package org.openqa.selenium.server.mock;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.mortbay.log.LogFactory;
import org.openqa.selenium.server.DefaultRemoteCommand;
import org.openqa.selenium.server.InjectionHelper;
import org.openqa.selenium.server.RemoteCommand;
import org.openqa.selenium.server.SeleniumServer;
import org.openqa.selenium.server.browserlaunchers.BrowserLauncherFactory;

public class MockPIFrameTest extends TestCase {
    
    static Log log = LogFactory.getLog(MockPIFrameTest.class);

    private static final String DRIVER_URL = "http://localhost:4444/selenium-server/driver/";
    private String sessionId;
    private SeleniumServer server;

    public void setUp() throws Exception {
        //System.setProperty("selenium.log", "c:\\dan\\scratch\\selenium.log");
        //SeleniumServer.setDebugMode(true);
        server = new SeleniumServer();
        server.setProxyInjectionMode(true);
        server.start();
        BrowserLauncherFactory.addBrowserLauncher("dummy", DummyBrowserLauncher.class);
        InjectionHelper.setFailOnError(false);
    }
    
    public void tearDown() {
        DummyBrowserLauncher.clearSessionId();
        InjectionHelper.setFailOnError(true);
        server.stop();
    }
    
    /** start a basic browser session */
    public void testStartSession() throws Exception {
        startSession();
    }
    /** start a basic browser session
     * 
     * @return the currently running MockPIFrame
     * @throws Exception
     */
    public MockPIFrame startSession() throws Exception {
        // 1. driver requests new session
        DriverRequest getNewBrowserSession = sendCommand("getNewBrowserSession", "*dummy", "http://x");
        // 2. server generates new session, awaits browser launch
        sessionId = waitForSessionId(getNewBrowserSession);
        // 3. browser starts, requests work
        MockPIFrame frame = new MockPIFrame(DRIVER_URL, sessionId, "frame1");
        BrowserRequest start = frame.seleniumStart();
        // 4. server automatically requests setContext
        RemoteCommand setContext = new DefaultRemoteCommand("setContext", sessionId, "");
        assertEquals("setContext command got mangled", setContext, start.getCommand());
        // 5. browser replies "OK" to setContext
        frame.sendResult("OK");
        // 6. server replies "OK,123" to driver
        assertEquals("getNewBrowserSession result got mangled", "OK,"+sessionId, getNewBrowserSession.getResult());
        return frame;
    }
    
    /** create a session and issue a valid "open" command */
    public void testRegularOpen() throws Exception {
        MockPIFrame frame1 = startSession();
        BrowserRequest browserRequest = frame1.getMostRecentRequest();
        
        // 1. driver issues an "open" command
        RemoteCommand open = new DefaultRemoteCommand("open", "blah.html", "");
        DriverRequest openRequest = sendCommand(open);
        // 2. original frame receives open request; replies "OK"
        assertEquals("open command got mangled", open, browserRequest.getCommand());
        frame1.sendResult("OK").getCommand();
        // 3. old frame unloads; new frame with same frame address loads and replies "START"
        MockPIFrame frame2 = new MockPIFrame(DRIVER_URL, sessionId, "frame2");
        frame2.seleniumStart();
        // 4. server automatically begins waiting for load; requests frame2 identify itself
        browserRequest = frame2.handleIdentifyCommand();
        // 5. server requests further identification, asks for "getTitle"; browser replies "blah.html"
        RemoteCommand getTitle = new DefaultRemoteCommand("getTitle", "", "");
        assertEquals("getTitle command got mangled", getTitle, browserRequest.getCommand());
        frame2.sendResult("blah.html");
        // 6. server replies "OK" to driver's original "open" command
        assertEquals("open result got mangled", "OK", openRequest.getResult());
    }
    
    /** create a session and issue a valid open command, simulating an out-of-order
     * response from the browser, as the new page load request comes in before the
     * "OK" from the original page
     */
    public void testEvilOpen() throws Exception {
        MockPIFrame frame1 = startSession();
        BrowserRequest browserRequest = frame1.getMostRecentRequest();
        
        // 1. driver issues an "open" command
        RemoteCommand open = new DefaultRemoteCommand("open", "blah.html", "");
        DriverRequest openRequest = sendCommand(open);
        // 2. original frame receives open request
        // ... but doesn't reply "OK" yet (this is the evil part!)
        assertEquals("open command got mangled", open, browserRequest.getCommand());
        // 3. old frame unloads; new frame with same frame address loads and replies "START"
        MockPIFrame frame2 = new MockPIFrame(DRIVER_URL, sessionId, "frame2");
        frame2.seleniumStart();
        // X. original frame finally manages to reply "OK" to original "open" command
        sleep(100);
        frame1.sendResult("OK");
        // 4. server automatically begins waiting for load; requests frame2 identify itself
        browserRequest = frame2.handleIdentifyCommand();
        // 5. server requests further identification, asks for "getTitle"; browser replies "blah.html"
        RemoteCommand getTitle = new DefaultRemoteCommand("getTitle", "", "");
        assertEquals("getTitle command got mangled", getTitle, browserRequest.getCommand());
        frame2.sendResult("blah.html");
        // 6. server replies "OK" to driver's original "open" command
        assertEquals("open result got mangled", "OK", openRequest.getResult());
    }
    
    /** Extracts a sessionId from the DummyBrowserLauncher, so we
     * can use it in our tests.  Note that the original driver request
     * won't be resolved until some MockPIFrame is launched with the
     * new sessionId, so we need to extract it prior to calling getNewBrowserSession.getResult().
     * @param getNewBrowserSession a not-yet-resolved request to get a new browser session; used to get an error message if we're forced to give up
     * @return the sessionId of the requested session
     * @throws InterruptedException
     */
    private String waitForSessionId(DriverRequest getNewBrowserSession) {
        // wait until timeout
        long now = System.currentTimeMillis();
        long timeout = AsyncHttpRequest.DEFAULT_TIMEOUT;
        long finish = now + timeout;
        if (timeout == 0) {
            finish = Long.MAX_VALUE;
        }
        sleep(10);
        String sessionId;
        String result = null;
        while (System.currentTimeMillis() < finish) {
            // DummyBrowserLauncher records its sessionId in a static variable; extract it here
            sessionId = DummyBrowserLauncher.getSessionId();
            if (sessionId != null) {
                return sessionId;
            }
            if (!getNewBrowserSession.isAlive()) {
                // something must have gone wrong 
                try {
                    result = getNewBrowserSession.getResult();
                } catch (Exception e) {
                    throw new RuntimeException("sessionId never appeared", e);
                }
                throw new RuntimeException("sessionId never appeared, getNewBrowserSession said: " + result);
            }
            // The DBL must not have been launched yet; keep waiting 
            sleep(10);
        }
        sessionId = DummyBrowserLauncher.getSessionId();
        if (sessionId != null) {
            return sessionId;
        }
        // sessionId never appeared; something must have gone wrong
        try {
            result = getNewBrowserSession.getResult();
        } catch (Exception e) {
            throw new RuntimeException("sessionId never appeared", e);
        }
        throw new RuntimeException("sessionId never appeared, getNewBrowserSession said: " + result);
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    private DriverRequest sendCommand(String cmd, String arg1, String arg2) {
        return sendCommand(new DefaultRemoteCommand(cmd, arg1, arg2), AsyncHttpRequest.DEFAULT_TIMEOUT);
    }
    
    private DriverRequest sendCommand(RemoteCommand cmd) {
        return sendCommand(cmd, AsyncHttpRequest.DEFAULT_TIMEOUT);
    }

    private DriverRequest sendCommand(RemoteCommand cmd, int timeoutInMillis) {
        log.info("Driver sends " + cmd + " on session " + sessionId);
        return DriverRequest.request(DRIVER_URL, cmd, sessionId, timeoutInMillis);
    }
}
