# halOS

halOS stands for HAL on OpenShift. It's a special [HAL](https://hal.github.io/) edition for WildFly instances managed by the  [WildFly operator](https://github.com/wildfly/wildfly-operator) and running on OpenShift.

## Architecture

![halos](halos.png)

halOS consists of two parts:

1. Proxy
2. [Console](https://github.com/hal/halos-console)

This repository contains the proxy. The proxy is a server side application running in a pod next to the WildFly instances. It talks to the management endpoints of the WildFly instances and collects data from these instances. 

#### Technical Stack

- [Quarkus](https://quarkus.io)
- JAX-RS
- Server Sent Events
- WildFly Controller Client
- HTTP server for the console
