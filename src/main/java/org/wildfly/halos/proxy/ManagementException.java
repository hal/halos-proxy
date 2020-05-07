package org.wildfly.halos.proxy;

public class ManagementException extends RuntimeException {

    public ManagementException(String message) {
        super(message);
    }

    public ManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}
