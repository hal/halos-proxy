/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wildfly.halos.proxy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.logging.Logger;

class UsernamePasswordAuthentication implements AuthenticationMechanism {

    static final Logger log = Logger.getLogger(UsernamePasswordAuthentication.class);

    @Override
    public ModelControllerClient authenticate(Container container) throws AuthenticationException {
        log.debugf("Try to connect to management endpoint %s", container);
        try {
            var address = InetAddress.getByName(container.ip());
            return ModelControllerClient.Factory.create("remote+http", address, container.port(), callbacks -> {
                for (Callback current : callbacks) {
                    if (current instanceof NameCallback ncb) {
                        ncb.setName("admin");
                    } else if (current instanceof PasswordCallback pcb) {
                        pcb.setPassword("admin".toCharArray());
                    } else if (current instanceof RealmCallback rcb) {
                        rcb.setText(rcb.getDefaultText());
                    } else {
                        throw new UnsupportedCallbackException(current);
                    }
                }
            });
        } catch (UnknownHostException e) {
            var error = String.format("Unable to connect to %s: %s", container, e.getMessage());
            log.error(error);
            throw new AuthenticationException(error, e);
        }
    }
}
