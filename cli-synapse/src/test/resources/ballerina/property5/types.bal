import ballerina/http;

public type Variables record {|
    string barProp1?;
    int barProp2?;
    string before?;
    string after?;
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
