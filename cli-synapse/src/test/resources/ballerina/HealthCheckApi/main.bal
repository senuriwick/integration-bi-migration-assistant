import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /healthcheck on httpListener {
    resource function get status(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            log:printInfo(string `message = ${"Health check requested"}`);
            ctx.payload = {"status": "UP"};
            check respond(ctx);
        } on fail error err {
        }
    }
}
