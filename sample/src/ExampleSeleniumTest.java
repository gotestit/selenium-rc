import junit.framework.TestCase;

import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.Selenium;

public class ExampleSeleniumTest extends TestCase {

    private Selenium browser;

    public void setUp() {
        browser = new DefaultSelenium("localhost", 4444, "*firefox",
                "http://www.google.com/");
        browser.start();
    }

    public void testGoogleImFeelingLucky() throws InterruptedException {
        browser.showContextualBanner();
        // relative URL is correct idiom for open()
        browser.open("/webhp?hl=en");
        // enter a search term
        browser.type("name=q", "Erlang");
        browser.click("btnI");
        browser.waitForPageToLoad("5000");

        // are we at erlang.org ?
        assertEquals("Erlang", browser.getTitle());
        String bodyText = browser.getBodyText();
        assertTrue(bodyText.contains("Software for a Concurrent World"));
    }

    public void tearDown() {
        browser.stop();
    }
}
