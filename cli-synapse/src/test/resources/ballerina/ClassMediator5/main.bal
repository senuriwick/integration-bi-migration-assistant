import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /audit on httpListener {
    resource function get scan(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"msg": "checking"};
            ctx.variables.who = "world";
            // TODO: Unsupported Synapse property 'statusInfo' (from classMediator.xml). The expression is not recognized by the property converter; manual conversion required.
            // Original Synapse:
            // <property name="statusInfo" scope="default" type="STRING" expression="get-property('axis2', 'HTTP_SC')"/>
            auditMediator(ctx, convertToString(ctx.variables.who));
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }
}
