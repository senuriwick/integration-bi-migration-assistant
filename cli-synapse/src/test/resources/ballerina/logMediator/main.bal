import ballerina/http;
import ballerina/log;

public listener http:Listener httpListener = new (8080);

service /logmediator on httpListener {
    resource function get custom(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.variables.user = "alice";
            log:printDebug(string `event = ${"login"} | user = ${convertToString(ctx.variables.user)}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get categories(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            log:printInfo(string `event = ${"info-event"}`);
            log:printDebug(string `event = ${"trace-event"}`);
            log:printDebug(string `event = ${"debug-event"}`);
            log:printWarn(string `event = ${"warn-event"}`);
            log:printError(string `event = ${"error-event"}`);
            log:printError(string `event = ${"fatal-event"}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get full(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            ctx.payload = {"ping": "pong"};
            // TODO: Synapse's 'full' log level also logs built-in fields (To/From/WSAction/SOAPAction/ReplyTo/MessageID, and correlation_id) that have no equivalent in the generated Context; only the listed <property> values and the current payload are logged. Manual conversion required for full parity.
            log:printInfo(string `event = ${"full-log"}, Payload: ${convertToString(ctx.payload)}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get invalid(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            // TODO: Unrecognized log level 'verbose'; falling back to 'simple'.

            // TODO: Synapse's 'simple' log level also logs built-in fields (To/From/WSAction/SOAPAction/ReplyTo/MessageID, and correlation_id) that have no equivalent in the generated Context; only the listed <property> values are logged. Manual conversion required for full parity.

            // TODO: Unrecognized log category 'SEVERE'; falling back to 'INFO'.
            log:printInfo(string `event = ${"bad-attrs"}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get emptySeparator(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            log:printInfo(string `a = ${1.toString()}b = ${2.toString()}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get unrecognizedChild(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            // TODO: A <log> child other than <property> is not supported; manual conversion required.
            // Original Synapse:
            // <message xmlns="http://ws.apache.org/ns/synapse">not a real log child</message>
            log:printInfo(string `event = ${"weird"}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get headers(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            // TODO: Synapse's 'headers' log level also logs built-in fields (correlation_id and the SOAP header blocks) that have no equivalent in the generated Context; only the listed <property> values are logged. Manual conversion required for full parity.
            log:printInfo(string `event = ${"headers-log"}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }

    resource function get omElement(http:Caller caller) returns error? {
        Context ctx = {variables: {}, caller: caller};
        do {
            log:printInfo(string `body = ${convertToString(xml `<foo xmlns="http://ws.apache.org/ns/synapse">bar</foo>`)}`);
            check respond(ctx);
        } on fail error err {
            log:printError("Unhandled error in mediation", 'error = err);
            ctx.payload = {"error": err.message()};
            ctx.statusCode = 500;
            check respond(ctx);
        }
    }
}
