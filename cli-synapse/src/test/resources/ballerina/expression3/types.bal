import ballerina/http;

public type Variables record {|
    string strVar?;
    int intVar?;
    int intFromString?;
    float floatFromString?;
    boolean boolFromString?;
    xml xmlFromString?;
    int intFromFloat?;
    int intFromBool?;
    string stringFromAny?;
    int intFromAny?;
    float floatFromAny?;
    boolean boolFromAny?;
    xml xmlFromAny?;
    json jsonFromAny?;
    int unsupported?;
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
