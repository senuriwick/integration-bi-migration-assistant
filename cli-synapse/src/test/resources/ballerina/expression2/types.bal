import ballerina/http;

public type Variables record {|
    string message?;
    string messageCopy?;
    string untypedMessage?;
    string untypedMessageCopy?;
    int integerValue?;
    int integerValueCopy?;
    int integerExpression?;
    int integerExpressionCopy?;
    string numericString?;
    string numericStringCopy?;
    int longFromInt?;
    float floatFromInt?;
    float doubleFromInt?;
    float floatFromIntExpression?;
    float floatValue?;
    float doubleFromFloat?;
    string stringFromFloat?;
    string stringFromBoolean?;
    string stringFromFloatExpression?;
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
