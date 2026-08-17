import ballerina/http;

public listener http:Listener httpListener = new (8080);

service /echo on httpListener {
    resource function get message(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            check respond(ctx);
        } on fail error err {
        }
    }

    resource function post message(http:Caller caller, http:Request request) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            check emitPayload(ctx, request);
            check respond(ctx);
        } on fail error err {
        }
    }
}
