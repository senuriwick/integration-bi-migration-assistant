import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /hello on httpListener {
    resource function get greet(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"msg": "hello"};
            ctx.variables.who = "world";
            greetMediator(ctx, "en", convertToString(ctx.variables.who));
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }
}
