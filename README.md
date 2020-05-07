# halOS

halOS stands for HAL on OpenShift. It's a special [HAL](https://hal.github.io/) edition for WildFly instances managed by the  [WildFly operator](https://github.com/wildfly/wildfly-operator) and running on OpenShift.

## Architecture

![halos](halos.png)

halOS consists of two parts:

1. Proxy
2. Console

This repository contains the proxy. The proxy is a server side application running in a pod next to the WildFly instances. It talks to the management endpoints of the WildFly instances and collects data from these instances. 

#### Technical Stack

- [Quarkus](https://quarkus.io)
- JAX-RS
- Server Sent Events
- WildFly Controller Client
- HTTP server for the console

### Console

The [console](https://github.com/hal/halos-console) is a [RIA](https://en.wikipedia.org/wiki/Rich_web_application) / [SPA](https://en.wikipedia.org/wiki/Single-page_application). The UI follows the design guidelines from [PatternFly](https://www.patternfly.org/v4/). 
