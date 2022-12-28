# halOS Proxy

halOS stands for **HAL** on **O**pen**S**hift. It is derived from [HAL](https://hal.github.io/)  and allows monitoring and inspection of WildFly and other services running on OpenShift. Although the focus is on WildFly, halOS supports monitoring arbitrary services. This is achieved through the concept of capabilities and extensions.

## Architecture

![halos](halos.png)

halOS consists of two parts:

1. Proxy (back-end, this repository)
2. [Console](https://github.com/hal/halos-console) (front-end)

This repository contains the proxy. The proxy is a microservice running next to the managed services. It connects to the managed services and interacts with them. It exposes a REST API that is consumed by the [console](https://github.com/hal/halos-console).  

## Technical Stack

- [Quarkus](https://quarkus.io)
- JAX-RS
- Server Sent Events
- fabric8 [OpenShift client](https://github.com/fabric8io/kubernetes-client)

## Build

```shell
./mvnw install
```

## Run

Please refer to the halOS [distribution](https://github.com/hal/halos-distribution#readme) about how to set up all services on OpenShift, start halOS and access the console.

## Development 

To run the proxy, you need to have access to an OpenShift cluster. The easiest way to get started is to use the [OpenShift sandbox](https://developers.redhat.com/developer-sandbox). The sandbox provides you with a private OpenShift environment in a shared, multi-tenant OpenShift cluster that is pre-configured with a set of developer tools.

Once you have access to your OpenShift cluster, create a [`.env`](https://quarkus.io/guides/config-reference#env-file) file in the `proxy` folder and add the following settings:

```shell
_DEV_QUARKUS_KUBERNETES-CLIENT_MASTER-URL=<OpenShift REST API>
_DEV_QUARKUS_KUBERNETES-CLIENT_TOKEN=<security token>
_DEV_QUARKUS_KUBERNETES-CLIENT_NAMESPACE=<your namespace>
```

Then start the proxy in dev mode, using

```shell
cd proxy
./mvnw quarkus:dev
```
