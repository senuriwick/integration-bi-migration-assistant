# Synapse to Ballerina migration report

1 Synapse construct could not be automatically converted and was left as TODOs in the generated code. Each entry shows the source file and the original Synapse code; review and migrate them manually.

## Unsupported property (1)

### `<property>` — classMediator.xml

The expression is not recognized by the property converter; manual conversion required.

```xml
<property name="statusInfo" scope="default" type="STRING" expression="get-property('axis2', 'HTTP_SC')"/>
```
