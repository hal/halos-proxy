package org.wildfly.halos.proxy;

import java.util.Objects;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** A WildFly instance with information how to access the management endpoint */
@RegisterForReflection
public class Instance implements Comparable<Instance> {

    public String name;
    public String host;
    public int port;
    public String username;
    public String password;

    @SuppressWarnings("unused")
    public Instance() {
        // used by JSON-B
    }

    public Instance(String name, String host, int port, String username, String password) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Instance instance = (Instance) o;
        return Objects.equals(name, instance.name);
    }

    @Override
    public int compareTo(Instance o) {
        return name.compareTo(o.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("%s@%s:%d", name, host, port);
    }
}
