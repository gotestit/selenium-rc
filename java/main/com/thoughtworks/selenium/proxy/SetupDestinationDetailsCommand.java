/*
  Copyright 2004 ThoughtWorks, Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.thoughtworks.selenium.proxy;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @version $Id: SetupDestinationDetailsCommand.java,v 1.4 2004/11/13 05:46:46 ahelleso Exp $
 */
public class SetupDestinationDetailsCommand implements RequestModificationCommand {
    public void execute(HTTPRequest httpRequest) {
        try {
            URI uri = new URI(SeleniumHTTPRequest.SELENIUM_REDIRECT_PROTOCOL + httpRequest.getHost());
            httpRequest.setDestinationServer(uri.getHost());
            httpRequest.setDestinationPort(getPort(uri));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Should have a valid host");
        }
    }

    private int getPort(URI uri) {
        int port = uri.getPort();
        return port == -1 ? 80 : port;
    }
}
