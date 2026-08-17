import ballerina/http;

public type Variables record {|
    string|int prop1?;
    int r1only?;
    boolean r2only?;
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
