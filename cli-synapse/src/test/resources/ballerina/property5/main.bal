import ballerina/http;

public listener http:Listener httpListener = new (8080);

service /chain on httpListener {
    resource function get run(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            check foo(ctx);
            check respond(ctx);
        } on fail error err {
        }
    }
}
