package com.thoughtworks.selenium

/**
 * The Groovy equivalent of SeleneseTestCase, as a GroovyTestCase.
 */
class GroovySeleneseTestCase extends GroovyTestCase {
    public static final BASE_METHODS = SeleneseTestBase.class.methods
    
    protected GroovySelenium selenium
    
    private SeleneseTestBase base
    private int defaultTimeout
    
    GroovySeleneseTestCase() {
        super()
        base = new SeleneseTestBase()
        defaultTimeout = 60000
    }
    
    @Override
    void setUp(String url = null,
        String browserString = base.runtimeBrowserString(),
        int port = base.getDefaultPort())
    {
        super.setUp()
        base.setUp(url, browserString, port)
        selenium = new GroovySelenium(base.selenium)
    }
    
    @Override
    void tearDown() {
        super.tearDown()
        base.tearDown()
    }
    
    /**
     * Returns the delegate for most Selenium API calls.
     */
    SeleneseTestBase getBase() {
        return base
    }
    
    void setDefaultTimeout(int timeout) {
        assert selenium != null
        
        defaultTimeout = timeout
        selenium.setDefaultTimeout(timeout)
    }
    
    void setAlwaysCaptureScreenshots(boolean capture) {
        selenium.setAlwaysCaptureScreenshots(capture)
    }
    
    void setCaptureScreenshotOnFailure(boolean capture) {
        selenium.setCaptureScreenshotOnFailure(capture)
    }
    
    void setTestContext() {
        selenium.setContext("${getClass().getSimpleName()}.${getName()}")
    }
    
    /**
     * Convenience method for conditional waiting. Returns when the condition
     * is satisfied, or fails the test if the timeout is reached.
     *
     * @param timeout    maximum time to wait for condition to be satisfied, in
     *                   milliseconds. If unspecified, the default timeout is
     *                   used; the default value can be set with
     *                   setDefaultTimeout().
     * @param condition  the condition to wait for. The Closure should return
     *                   true when the condition is satisfied.
     */
    void waitFor(int timeout = defaultTimeout, Closure condition) {
        assert timeout > 0
        
        def timeoutTime = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < timeoutTime) {
            try {
                if (condition.call()) {
                    return
                }
            }
            catch (e) {}
            sleep(500)
        }
        
        fail('timeout')
    }
    
    /**
     * Delegates missing method calls to the SeleneseTestBase object where
     * possible.
     *
     * @param name
     * @param args
     */
    def methodMissing(String name, args) {
        def method = BASE_METHODS.find { it.getName() == name }
        if (method) {
            return method.invoke(base, args)
        }
        
        throw new MissingMethodException(name, getClass(), args)
    }
}
