import ballerina/http;

public listener http:Listener httpListener = new (8080);

service /HelloWorld on httpListener {
    resource function get status/[string name]/[string id](string q, http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"Hello": "World"};
            check foo(ctx);
        } on fail error err {
        }
    }

    resource function get id1/[string id](http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"Hello": "World"};
            check bar(ctx);
        } on fail error err {
        }
    }
}
