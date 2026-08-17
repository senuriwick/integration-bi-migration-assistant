import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /orders on httpListener {
    resource function post create(http:Caller caller, http:Request request) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            check emitPayload(ctx, request);
            // TODO: Unsupported Synapse mediator '<filter>' (from api.xml). Control-flow mediator not supported; the wrapper logic is not applied and nested mediators below need manual restructuring.
            // Original Synapse:
            // <filter regex="premium" source="$ctx:type" xmlns="http://ws.apache.org/ns/synapse">
            //                 <then>
            //                     <property name="tier" scope="default" value="premium"/>
            //                 </then>
            //                 <else>
            //                     <property name="tier" scope="default" value="standard"/>
            //                 </else>
            //             </filter>
            ctx.variables.tier = "premium";
            ctx.variables.tier = "standard";
            ctx.payload = {"ok": true};
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }
}
