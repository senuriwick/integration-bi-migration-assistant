import ballerina/http;
import ballerina/log;

function JMSErrorSeq(Context ctx) returns error? {
    // TODO: unsupported get-property(...) call in expression: 'get-property('ERROR_CODE')'
    log:printError(string `message = ${"JMS error"}, errorMessage = ${convertToString(ctx.variables.ERROR_MESSAGE)}`);
    // TODO: Unsupported Synapse mediator '<drop>' (from JMSErrorSeq.xml). Mediator not supported; manual conversion required.
    // Original Synapse:
    // <drop xmlns="http://ws.apache.org/ns/synapse"/>
}

function JMSInjectingSeq(Context ctx) returns error? {
    log:printInfo(string `message = ${"JMS message received"}, body = ${convertToString(ctx.payload)}`);
    // TODO: Unsupported Synapse mediator '<drop>' (from JMSInjectingSeq.xml). Mediator not supported; manual conversion required.
    // Original Synapse:
    // <drop xmlns="http://ws.apache.org/ns/synapse"/>
}

function convertToString(anydata v) returns string {
    return v.toString();
}

function respond(Context ctx) returns error? {
    http:Response response = new;
    response.setPayload(ctx.payload);
    foreach [string, string] [name, value] in ctx.headers.entries() {
        response.setHeader(name, value);
    }
    int? statusCode = ctx.statusCode;
    if statusCode is int {
        response.statusCode = statusCode;
    }
    check (<http:Caller>ctx.caller)->respond(response);
}

function emitPayload(Context ctx, http:Request request) returns error? {
    string contentType = request.getContentType();
    if contentType.startsWith("application/json") {
        ctx.payload = check request.getJsonPayload();
    } else if contentType.startsWith("application/xml") || contentType.startsWith("text/xml") {
        ctx.payload = check request.getXmlPayload();
    } else if contentType.startsWith("text/") {
        ctx.payload = check request.getTextPayload();
    } else {
        ctx.payload = check request.getBinaryPayload();
    }
}
