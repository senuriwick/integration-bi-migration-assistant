import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /respond on httpListener {
    resource function get process(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"msg": "processing"};
            respond2(ctx);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }
}
