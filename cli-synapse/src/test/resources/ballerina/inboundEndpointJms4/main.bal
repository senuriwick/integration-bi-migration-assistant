import ballerinax/activemq.driver as _;
import ballerinax/java.jms;

configurable string jMSInboundEndpointInitialContextFactory = "org.apache.activemq.jndi.ActiveMQInitialContextFactory";
configurable string jMSInboundEndpointProviderUrl = "tcp://localhost:61616";

public listener jms:Listener jMSInboundEndpointListener = new jms:Listener(
    connectionConfig = {initialContextFactory: jMSInboundEndpointInitialContextFactory, providerUrl: jMSInboundEndpointProviderUrl},
    consumerOptions = {
        destination: {
            'type: jms:QUEUE,
            name: "TestQueue"
        }
    }
);

service "JMSInboundEndpoint" on jMSInboundEndpointListener {
    remote function onMessage(jms:Message message, jms:Caller caller) returns error? {
        Context ctx = {variables: {}};
        do {
            if message !is jms:TextMessage {
                fail error("Unsupported JMS message type: expected a TextMessage");
            }
            ctx.payload = message.content;
            check JMSInjectingSeq(ctx);
        } on fail error err {
            ctx.variables.ERROR_MESSAGE = err.message();
            check JMSErrorSeq(ctx);
        }
    }
}
