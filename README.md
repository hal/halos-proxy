# halOS Proxy

halOS stands for HAL on OpenShift. It's a special [HAL](https://hal.github.io/) edition for WildFly instances running on OpenShift.

## Architecture

![halos](halos.png)

halOS consists of two parts:

1. Proxy (this repository)
2. [Console](https://github.com/hal/halos-console)

This repository contains the proxy. The proxy is a microservice running next to WildFly instances. It talks to the management endpoints of the WildFly instances and exposes a REST API. It also serves the static resources of the [console](https://github.com/hal/halos-console).  

## Technical Stack

- [Quarkus](https://quarkus.io)
- JAX-RS
- Server Sent Events
- WildFly Controller Client

## Build

Pending