# Synapse to Ballerina migration report

6 Synapse constructs could not be automatically converted and were left as TODOs in the generated code. Each entry shows the source file and the original Synapse code; review and migrate them manually.

## Partially supported log level (3)

### `<log>` — logMediator.xml

Synapse's 'full' log level also logs built-in fields (To/From/WSAction/SOAPAction/ReplyTo/MessageID, and correlation_id) that have no equivalent in the generated Context; only the listed <property> values and the current payload are logged. Manual conversion required for full parity.

```xml
<log level="full">
```

### `<log>` — logMediator.xml

Synapse's 'simple' log level also logs built-in fields (To/From/WSAction/SOAPAction/ReplyTo/MessageID, and correlation_id) that have no equivalent in the generated Context; only the listed <property> values are logged. Manual conversion required for full parity.

```xml
<log level="simple">
```

### `<log>` — logMediator.xml

Synapse's 'headers' log level also logs built-in fields (correlation_id and the SOAP header blocks) that have no equivalent in the generated Context; only the listed <property> values are logged. Manual conversion required for full parity.

```xml
<log level="headers">
```

## Unsupported log attribute (2)

### `<log>` — logMediator.xml

Unrecognized log level 'verbose'; falling back to 'simple'.

```xml
<log level="verbose">
```

### `<log>` — logMediator.xml

Unrecognized log category 'SEVERE'; falling back to 'INFO'.

```xml
<log category="SEVERE">
```

## Unsupported log child (1)

### `<log>` — logMediator.xml

A <log> child other than <property> is not supported; manual conversion required.

```xml
<message xmlns="http://ws.apache.org/ns/synapse">not a real log child</message>
```
