import ballerina/http;

public type Variables record {|
    float discount?;
    anydata summary?;
    float surcharge?;
|};

public type Context record {|
    Variables variables;
    anydata payload = ();
    map<string> headers = {};
    map<anydata> axis2 = {};
    int statusCode?;
    http:Caller caller?;
    boolean responded = false;
|};
