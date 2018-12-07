package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class SupervisedActor extends AbstractActor {

    public static Props props() {
        return Props.create(SupervisedActor.class, SupervisedActor::new);
    }

    @Override
    public void preStart() throws Exception {
        System.out.println("supervised actor started");
    }

    @Override
    public void postStop() throws Exception {
        System.out.println("supervised actor stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchEquals("fail", f -> {
                    System.out.println("supervised actor fails now");
                    throw new Exception("I failed!");
                }).build();
    }
}
