# Synapse to Ballerina migration report

9 Synapse constructs could not be automatically converted and were left as TODOs in the generated code. Each entry shows the source file and the original Synapse code; review and migrate them manually.

## Unsupported mediator (2)

### `<drop>` — JMSErrorSeq.xml

Mediator not supported; manual conversion required.

```xml
<drop xmlns="http://ws.apache.org/ns/synapse"/>
```

### `<drop>` — JMSInjectingSeq.xml

Mediator not supported; manual conversion required.

```xml
<drop xmlns="http://ws.apache.org/ns/synapse"/>
```

## Unsupported inbound endpoint parameter (7)

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'interval' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="interval">1000</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'sequential' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="sequential">true</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'coordination' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="coordination">true</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'transport.jms.ConnectionFactoryJNDIName' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="transport.jms.ConnectionFactoryJNDIName">QueueConnectionFactory</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'transport.jms.SessionTransacted' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="transport.jms.SessionTransacted">false</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'transport.jms.SessionAcknowledgement' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="transport.jms.SessionAcknowledgement">AUTO_ACKNOWLEDGE</parameter>
```

### `<parameter>` — inboundEndpoint.xml

Inbound endpoint 'JMSInboundEndpoint' parameter 'transport.jms.ContentType' is not mapped to any Ballerina construct; manual conversion required.

```xml
<parameter name="transport.jms.ContentType">application/json</parameter>
```
