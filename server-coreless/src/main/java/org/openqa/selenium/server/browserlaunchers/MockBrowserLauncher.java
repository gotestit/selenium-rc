/*
 * Created on Oct 25, 2006
 *
 */
package org.openqa.selenium.server.browserlaunchers;

import java.io.*;
import java.net.*;

import org.openqa.selenium.server.*;

public class MockBrowserLauncher implements BrowserLauncher, Runnable {

    private final int port;
    private final String sessionId;
    private Thread browser;
    private boolean interrupted = false;
    
    public MockBrowserLauncher(int port, String sessionId) {
        this.port = port;
        this.sessionId = sessionId;
    }
    
    public MockBrowserLauncher(int port, String sessionId, String command) {
        this.port = port;
        this.sessionId = sessionId;
    }
    
    
    
    public void launchRemoteSession(String url, boolean multiWindow) {
        browser = new Thread(this);
        browser.setName("mockbrowser");
        browser.start();
    }

    public void launchHTMLSuite(String startURL, String suiteUrl,
            boolean multiWindow) {

    }

    public void close() {
        interrupted = true;
        browser.interrupt();
        
    }

    public void run() {
        try {
            String startURL = "http://localhost:" + port+"/selenium-server/driver/?sessionId=" + sessionId;
            String commandLine = doBrowserRequest(startURL+"&seleniumStart=true", "START");
            while (!interrupted) {
                System.out.println("MOCK: " + commandLine);
                SeleneseCommand sc = DefaultSeleneseCommand.parse(commandLine);
                String command = sc.getCommand();
                String result = "OK";
                if (command.startsWith("get")) {
                    result = "OK,x";
                } else if (command.startsWith("is")) {
                    result = "OK,true";
                }
                if (SeleniumServer.isDebugMode() && !interrupted) {
                    for (int i = 0; i < 3; i++) {
                        doBrowserRequest(startURL + "&logging=true", "logLevel=debug:dummy log message " + i + "\n");
                    }
                }
                if (!interrupted) {
                    commandLine = doBrowserRequest(startURL, result);
                }
            }
            System.out.println("MOCK: interrupted, exiting");
        } catch (Exception e) {
            RuntimeException re = new RuntimeException("Exception in mock browser", e);
            re.printStackTrace();
            throw re;
        }
    }
    
    private String stringContentsOfInputStream(InputStream is) throws IOException {
        StringBuffer sb = new StringBuffer();
        InputStreamReader r = new InputStreamReader(is, "UTF-8");
        int c;
        while ((c = r.read()) != -1) {
            sb.append((char) c);
        }
        return sb.toString();
    }
    
    private String doBrowserRequest(String url, String body) throws IOException {
        int responsecode = 200;
        URL result = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) result.openConnection();
        
        conn.setRequestProperty("Content-Type", "application/xml");
        // Send POST output.
        conn.setDoOutput(true);
        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
        wr.write(body);
        wr.flush();
        wr.close();
        //conn.setInstanceFollowRedirects(false);
        //responsecode = conn.getResponseCode();
        if (responsecode == 301) {
            String pathToServlet = conn.getRequestProperty("Location");
            throw new RuntimeException("Bug! 301 redirect??? " + pathToServlet);
        } else if (responsecode != 200) {
            throw new RuntimeException(conn.getResponseMessage());
        } else {
            InputStream is = conn.getInputStream();
            return stringContentsOfInputStream(is);
        }
    }

}