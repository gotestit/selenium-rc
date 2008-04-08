package org.openqa.selenium.server;

import junit.framework.TestCase;

public class HtmlIdentifierTest extends TestCase {
    
    public void setUp() throws Exception {
        SeleniumServer.setBrowserSideLogEnabled(true);
        SeleniumServer.configureLogging(new RemoteControlConfiguration());
    }
    
    public void testMetaEquiv() {
        boolean result = HtmlIdentifier.shouldBeInjected("/selenium-server/tests/proxy_injection_meta_equiv_test.js", 
                "application/x-javascript",
                "<!DOCTYPE html PUBLIC \\\"-//W3C//DTD XHTML 1.0 Transitional//EN \\\" \\\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\\\"><html xmlns=\\\"http://www.w3.org/1999/xhtml\\\">\\n<head>\\n  <meta http-equiv=\\\"Content-Type\\\" content=\\\"text/html; charset=ISO-8859-\"; var s2=\"1\\\" />\\n  <title>Insert</title>\\n</head>\\n<body>n<p><strong>DWR tests passed</strong></p>\\n\\n</body>\\n</html>\\n\";");
        assertEquals("improper injection", false, result);
    }
    
    public void testGoogleScenario() {
        boolean result = HtmlIdentifier.shouldBeInjected("http://www.google.com/webhp", 
                "text/html; charset=UTF-8", 
                "<html>...</html>");
        assertEquals("improper injection", true, result);
    }

    public void testStupidDellDotComScenario() {
        boolean result = HtmlIdentifier.shouldBeInjected("/menu.htm", "text/html", "var x = ''; someOtherJavaScript++; blahblahblah;");
        assertEquals("improper injection", false, result);
    }

}
