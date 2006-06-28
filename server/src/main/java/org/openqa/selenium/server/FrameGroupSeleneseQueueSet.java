package org.openqa.selenium.server;
/*
 * Copyright 2006 BEA, Inc.
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


import java.util.HashMap;
import java.util.Map;

/**
 * <p>Manages sets of SeleneseQueues corresponding to windows and frames in a single browser session.</p>
 * 
 * @author nelsons
 */
public class FrameGroupSeleneseQueueSet {
    private static String expectedNewWindowName;
    /**
     * JavaScript expression telling where the frame is within the current window (i.e., "local"
     * to the current window).
     */
    private String currentLocalFrameAddress;
    /**
     * the name of the user-level window in selenium's record-keeping.
     * 
     * The initial browser window has a blank name.  When a test calls waitForPopUp, that call's
     * argument is the window name as far as selenium is concerned.
     */
    private String currentSeleniumWindowName;
    /**
     * combines currentSeleniumWindowName and currentLocalFrameAddress to form an address of a frame
     * which is unique across all windows
     */
    private FrameAddress currentFrameAddress = null;
    
    private Map<FrameAddress, SeleneseQueue> frameAddressToSeleneseQueue = new HashMap<FrameAddress, SeleneseQueue>();
    
    private Map<FrameAddress, Boolean> frameAddressToJustLoaded = new HashMap<FrameAddress, Boolean>();
    /**
     * A unique string denoting a session with a browser.  In most cases this session begins with the
     * selenium server configuring and starting a browser process, and ends with a selenium server killing 
     * that process.
     */
    private final String sessionId;
    
    public static final String DEFAULT_LOCAL_FRAME_ADDRESS = "top";
    /**
     * Each user-visible window group has a selenium window name.  The name of the initial browser window is "".
     * Even if the page reloads, the JavaScript is able to determine that it is this initial window because
     * window.opener==null.  Any window for whom window.opener!=null is a "pop-up".
     */
    public static final String DEFAULT_SELENIUM_WINDOW_NAME = "";
    /**
     * Each user-visible window group has a selenium window name.  The name of the initial browser window is "".
     * Even if the page reloads, the JavaScript is able to determined that it is this initial window because
     * window.opener==null.  Any window for whom window.opener!=null is a "pop-up".  
     * 
     * When a pop-up reloads, it can see that it is not in the initial window.  It will not know which window
     * it is until selenium tells it as part of the information sent with the next command.  Until that
     * happens, use this placeholder for the unknown name.
     */
    public static final String SELENIUM_WINDOW_NAME_UNKNOWN_POPUP = "?";
    
    public FrameGroupSeleneseQueueSet(String sessionId) {
        this.sessionId = sessionId;
        setCurrentFrameAddress(new FrameAddress(DEFAULT_SELENIUM_WINDOW_NAME, DEFAULT_LOCAL_FRAME_ADDRESS));
    }
    
    private void selectWindow(String seleniumWindowName) {
        if ("null".equals(seleniumWindowName)) {
            // this results from only working with strings over the wire for Selenese
            currentSeleniumWindowName = DEFAULT_SELENIUM_WINDOW_NAME;
        }
        else {
            currentSeleniumWindowName = seleniumWindowName;
        }
        selectFrame(DEFAULT_LOCAL_FRAME_ADDRESS);            
    }
    
    public SeleneseQueue getSeleneseQueue() {
        return getSeleneseQueue(currentFrameAddress);
    }

    public SeleneseQueue getSeleneseQueue(FrameAddress frameAddress) {
        synchronized(frameAddressToSeleneseQueue) {
            if (!frameAddressToSeleneseQueue.containsKey(frameAddress)) {
                if (SeleniumServer.isDebugMode()) {
                    System.out.println("---------allocating new SeleneseQueue for " + frameAddress + ".");
                }
                frameAddressToSeleneseQueue.put(frameAddress, new SeleneseQueue(sessionId, currentSeleniumWindowName));
            }
            else {
                if (SeleniumServer.isDebugMode()) {
                    System.out.println("---------retrieving SeleneseQueue for " + frameAddress + ".");
                }
            }
        }
        return frameAddressToSeleneseQueue.get(frameAddress);
    }
    
    private void selectFrame(String localFrameAddress) {
        setCurrentLocalFrameAddress(localFrameAddress);
    }
    
    /** Schedules the specified command to be retrieved by the next call to
     * handle command result, and returns the result of that command.
     * 
     * @param command - the Selenese command verb
     * @param field - the first Selenese argument (meaning depends on the verb)
     * @param value - the second Selenese argument
     * @return - the command result, defined by the Selenese JavaScript.  "getX" style
     * commands may return data from the browser; other "doX" style commands may just
     * return "OK" or an error message.
     */
    public String doCommand(String command, String arg, String value) {
        if (SeleniumServer.isProxyInjectionMode()) {
            if (command.equals("selectFrame")) {
                if ("".equals(arg)) {
                    selectFrame(DEFAULT_LOCAL_FRAME_ADDRESS);
                    return "OK";
                }
                boolean newFrameFound = false;
                for (FrameAddress frameAddress : frameAddressToSeleneseQueue.keySet()) {
                    if (frameAddress.getWindowName().equals(currentSeleniumWindowName)) {                        
                        SeleneseQueue frameQ = frameAddressToSeleneseQueue.get(frameAddress);
                        String frameMatchBooleanString = frameQ.doCommand("isFrame", currentLocalFrameAddress, arg);
                        if ("OK,true".equals(frameMatchBooleanString)) {
                            setCurrentFrameAddress(frameAddress);
                            newFrameFound = true;
                            break;
                        }
                    }
                }
                if (!newFrameFound) {
                    return "ERROR: starting from frame " + currentFrameAddress 
                    + ", could not find frame " + arg;
                }                
                return "OK";
            }
            if (command.equals("selectWindow")) {
                selectWindow(arg);
                return "OK";
            }
            if (command.equals("waitForPopUp")) {
                return waitForPopUp(arg, Integer.parseInt(value));
            }
            if (command.equals("waitForPageToLoad")) {
                if (justLoaded(currentFrameAddress)) {
                    System.out.println("Not requesting waitForPageToLoad since just loaded "
                            + currentFrameAddress);
                    markWhetherJustLoaded(currentFrameAddress, false); // only do this trick once
                    return "OK";
                }
                String result = getSeleneseQueue().waitForResult();
                if (justLoaded(currentFrameAddress)) { // happened during waitForResult call
                    markWhetherJustLoaded(currentFrameAddress, false);   // reset this recordkeeping
                }
                return result;
            }
        } // if (SeleniumServer.isProxyInjectionMode())
        return getSeleneseQueue().doCommand(command, arg, value);
    }
    
    private String waitForPopUp(String seleniumWindowName, int timeout) {
        setExpectedNewWindowName(seleniumWindowName);
        selectWindow(seleniumWindowName);
        synchronized(this) {
            if (justLoaded(currentFrameAddress)) {
                return "OK";  // since no one who was waiting when this window arrived, its result was discarded
            }
        }
        return getSeleneseQueue().waitForResult(timeout);
    }

    /**
     * <p>Accepts a command reply, and retrieves the next command to run.</p>
     * 
     * 
     * @param commandResult - the reply from the previous command, or null
     * @param frameAddress - frame from which the reply came
     * @param uniqueId 
     * @return - the next command to run
     */
    public SeleneseCommand handleCommandResult(String commandResult, FrameAddress frameAddress, String uniqueId) {
        synchronized(this) {
            if (frameAddress.getWindowName().equals(SELENIUM_WINDOW_NAME_UNKNOWN_POPUP)) {
                for (FrameAddress f : frameAddressToSeleneseQueue.keySet()) {
                    // the situation being handled here: a pop-up window has either just loaded or reloaded, and therefore
                    // doesn't know its name.  It uses SELENIUM_WINDOW_NAME_UNKNOWN_POPUP as a placeholder.
                    // Meanwhile, on the selenium server-side, a thread is waiting for this result.
                    //
                    // To determine if this has happened, we cycle through all of the SeleneseQueue objects,
                    // looking for ones with a matching local frame address (e.g., top.frames[1]), is also a
                    // pop-up, and which has a thread waiting on a result.  If all of these conditions hold,
                    // then we figure this queue is the one that we want:
                    if (f.getLocalFrameAddress().equals(frameAddress.getLocalFrameAddress())
                            && !f.getWindowName().equals(DEFAULT_SELENIUM_WINDOW_NAME)
                            && frameAddressToSeleneseQueue.get(f).getCommandResultHolder().hasBlockedGetter()) {
                        frameAddress = f;
                        break;
                    }
                }
            }
        }
        SeleneseQueue queue = getSeleneseQueue(frameAddress);
        queue.setUniqueId(uniqueId);
        return queue.handleCommandResult(commandResult);
    }
    
    /**
     * <p> Throw away a command reply.
     *
     */
    public void discardCommandResult() {
        getSeleneseQueue().discardCommandResult();
    }
    
    /**
     * <p> Empty queues, and thereby wake up any threads that are hanging around.
     *
     */
    public void endOfLife() {
        for (SeleneseQueue frameQ : frameAddressToSeleneseQueue.values()) {
            frameQ.endOfLife();
        }
    }
    
    public boolean justLoaded(FrameAddress frameAddress) {
        return (frameAddressToJustLoaded.containsKey(frameAddress));
    }
    
    public void markWhetherJustLoaded(FrameAddress frameAddress, boolean justLoaded) {
        boolean oldState = justLoaded(frameAddress);
        if (oldState!=justLoaded) {
            if (justLoaded) {
                System.out.println(frameAddress + " marked as just loaded");
                frameAddressToJustLoaded.put(frameAddress, true);
            }
            else {
                System.out.println(frameAddress + " marked as NOT just loaded");
                frameAddressToJustLoaded.remove(frameAddress);
            }
        }
    }
    
    public String getCurrentLocalFrameAddress() {
        return currentLocalFrameAddress;
    }
    
    public void setCurrentLocalFrameAddress(String localFrameAddress) {
        this.setCurrentFrameAddress(new FrameAddress(currentSeleniumWindowName, localFrameAddress));
    }
    
    public String getCurrentSeleniumWindowName() {
        return currentSeleniumWindowName;
    }
    
    public void setCurrentSeleniumWindowName(String seleniumWindowName) {
        this.setCurrentFrameAddress(new FrameAddress(seleniumWindowName, DEFAULT_LOCAL_FRAME_ADDRESS));             
    }
    
    public FrameAddress getCurrentFrameAddress() {
        return currentFrameAddress;
    }
    
    public void setCurrentFrameAddress(FrameAddress frameAddress) {
        this.currentFrameAddress = frameAddress;
        this.currentSeleniumWindowName = frameAddress.getWindowName();
        this.currentLocalFrameAddress = frameAddress.getLocalFrameAddress();
        
        if (SeleniumServer.isDebugMode()) {
            System.out.println("Current frame address set to " + currentFrameAddress + ".");
        }
    }
    
    public static synchronized FrameAddress findFrameAddress(String seleniumWindowName, String localFrameAddress, boolean justLoaded) {
        if (seleniumWindowName.equals(SELENIUM_WINDOW_NAME_UNKNOWN_POPUP) && justLoaded && expectedNewWindowName!=null) {
            seleniumWindowName = expectedNewWindowName;
            expectedNewWindowName = null;
        }
        return new FrameAddress(seleniumWindowName, localFrameAddress);
    }

    public static String getExpectedNewWindowName() {
        return expectedNewWindowName;
    }

    public static void setExpectedNewWindowName(String expectedNewWindowName) {
        FrameGroupSeleneseQueueSet.expectedNewWindowName = expectedNewWindowName;
    }
}
