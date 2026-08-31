import ballerina/file;

configurable string fileInboundEndpointPath = "C:/projects/Test/inbound/input";

public listener file:Listener fileInboundEndpointListener = new (
    path = fileInboundEndpointPath,
    recursive = false
);

// Synapse VFS inbound endpoints process each discovered file exactly once; there is no onModify equivalent.
service on fileInboundEndpointListener {
    remote function onCreate(file:FileEvent event) returns error? {
        Context ctx = {variables: {}};
        do {
            check FileProcessSequence();
        } on fail error err {
            ctx.variables.ERROR_MESSAGE = err.message();
            check FileErrorSequence(ctx);
        }
    }
}
