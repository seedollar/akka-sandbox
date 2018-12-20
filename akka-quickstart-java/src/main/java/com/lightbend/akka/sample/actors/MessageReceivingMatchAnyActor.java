package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.akka.sample.protocol.MessageReceivingProtocol;

public class MessageReceivingActor extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(MessageReceivingActor.class, MessageReceivingActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                // Match any message
                .matchAny(msg -> log.info("Message: {}",
                        (msg instanceof MessageReceivingProtocol.ReceivingMessage) ?
                                ((MessageReceivingProtocol.ReceivingMessage)msg).getMessage():
                                msg.toString()))
                .build();
    }
}
