import ballerina/http;

public type Variables record {|
    string greeting?;
    int count1?;
    string count2?;
    boolean enabled?;
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
