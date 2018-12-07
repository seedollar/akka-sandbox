package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class StartStopActor2 extends AbstractActor {

    public static Props props() {
        return Props.create(StartStopActor2.class, StartStopActor2::new);
    }

    @Override
    public void preStart() {
        System.out.println("second started");
    }

    @Override
    public void postStop() {
        System.out.println("second stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}
