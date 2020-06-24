package org.wildfly.halos.proxy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.halos.proxy.InstanceModification.Modification;

import static java.util.Collections.synchronizedSortedMap;

/** Manages connections to the WildFly management endpoints and execute DMR operations. */
@ApplicationScoped
class Instances {

    private static final String REMOTE_HTTP = "remote+http";
    private static final Logger log = Logger.getLogger("halos.proxy.dispatcher");

    private final SortedMap<Instance, ModelControllerClient> clients;
    private final UnicastProcessor<InstanceModification> processor;
    private final Multi<InstanceModification> modifications;

    @Inject
    Instances() {
        this.clients = synchronizedSortedMap(new TreeMap<>());
        this.processor = UnicastProcessor.create();
        this.modifications = processor
                .broadcast().toAllSubscribers()
                .on().overflow().dropPreviousItems();
    }

    void register(Instance instance) throws ManagementException {
        try {
            InetAddress address = InetAddress.getByName(instance.host);
            ModelControllerClient client = ModelControllerClient.Factory.create(REMOTE_HTTP, address,
                    instance.port, callbacks -> {
                        for (Callback current : callbacks) {
                            if (current instanceof NameCallback) {
                                NameCallback ncb = (NameCallback) current;
                                ncb.setName(instance.username);
                            } else if (current instanceof PasswordCallback) {
                                PasswordCallback pcb = (PasswordCallback) current;
                                pcb.setPassword(instance.password.toCharArray());
                            } else if (current instanceof RealmCallback) {
                                RealmCallback rcb = (RealmCallback) current;
                                rcb.setText(rcb.getDefaultText());
                            } else {
                                throw new UnsupportedCallbackException(current);
                            }
                        }
                    });
            ModelControllerClient added = clients.put(instance, client);
            Modification modification = added != null ? Modification.MODIFIED : Modification.ADDED;
            processor.onNext(new InstanceModification(modification, instance.name));
            log.infof("Registered client for %s.", instance);
        } catch (UnknownHostException e) {
            String error = String.format("Unable to connect to instance %s: %s", instance, e.getMessage());
            log.error(error);
            throw new ManagementException(error, e);
        }
    }

    void unregister(String name) throws ManagementException {
        Map.Entry<Instance, ModelControllerClient> entry = findByName(name);
        if (entry != null) {
            Instance instance = entry.getKey();
            ModelControllerClient client = entry.getValue();
            try {
                client.close();
                log.infof("Closed client for %s", instance);
            } catch (IOException e) {
                String error = String.format("Unable to close client for %s: %s", instance, e.getMessage());
                log.error(error);
                throw new ManagementException(error, e);
            } finally {
                clients.remove(instance);
                processor.onNext(new InstanceModification(Modification.REMOVED, instance.name));
            }
        }
    }

    boolean isEmpty() {
        return clients.isEmpty();
    }

    boolean hasInstance(String name) {
        return findByName(name) != null;
    }

    public List<Instance> instances() {
        return List.copyOf(clients.keySet());
    }

    Multi<InstanceModification> modifications() {
        return modifications;
    }

    ModelNode execute(Operation operation) {
        ModelNode result = new ModelNode();
        clients.forEach((instance, client) -> executeAndWrap(instance.name, client, operation, instance, result));
        return result;
    }

    ModelNode executeSingle(String name, Operation operation) {
        Map.Entry<Instance, ModelControllerClient> entry = findByName(name);
        if (entry != null) {
            ModelNode result = new ModelNode();
            executeAndWrap(name, entry.getValue(), operation, entry.getKey(), result);
            return result;
        } else {
            log.errorf("Unable to find client for instance %1s. Did you register %1s?", name);
            return null;
        }
    }

    private void executeAndWrap(String name, ModelControllerClient client, Operation operation,
            Instance instance, ModelNode modelNode) {
        ModelNode result;
        try {
            result = client.execute(operation);
        } catch (IOException e) {
            log.errorf("Error executing operation %s against %s: %s",
                    operation.getOperation().toJSONString(true), instance, e.getMessage());
            if (e.getCause() instanceof ConnectException) {
                unregister(name);
            }
            result = new ModelNode();
            result.get(ClientConstants.OUTCOME).set("failed");
            result.get(ClientConstants.FAILURE_DESCRIPTION).set(e.getMessage());
        }
        modelNode.get(instance.name).set(result);
    }

    private Map.Entry<Instance, ModelControllerClient> findByName(String name) {
        for (Map.Entry<Instance, ModelControllerClient> entry : clients.entrySet()) {
            if (name.equals(entry.getKey().name)) {
                return entry;
            }
        }
        return null;
    }
}
