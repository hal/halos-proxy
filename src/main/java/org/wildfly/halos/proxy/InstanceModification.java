package org.wildfly.halos.proxy;

class InstanceModification {

    Modification modification;
    String instance;

    InstanceModification(Modification modification, String instance) {
        this.modification = modification;
        this.instance = instance;
    }

    @Override
    public String toString() {
        return modification.name() + "," + instance;
    }

    public enum Modification {
        ADDED, MODIFIED, REMOVED
    }
}
