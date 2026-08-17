import ballerina/http;

public listener http:Listener httpListener = new (8080);

service /healthcheck on httpListener {
    resource function get status(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            // TODO: Unsupported Synapse mediator '<log>' (from HealthCheckApi.xml). Mediator not supported; manual conversion required.
            // Original Synapse:
            // <log level="custom" xmlns="http://ws.apache.org/ns/synapse">
            //                 <property name="message" value="Health check requested"/>
            //             </log>
            ctx.payload = {"status": "UP"};
            check respond(ctx);
        } on fail error err {
        }
    }
}
