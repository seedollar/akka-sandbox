package com.lightbend.akka.sample.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.sample.actors.StartStopActor1;

public class StartStopActorMain {

    public static void main(String[] args) {
        ActorSystem actorSystem = ActorSystem.create("startStop");

        ActorRef first = actorSystem.actorOf(StartStopActor1.props(), "first");
        first.tell("stop", ActorRef.noSender());
    }
}
