package com.lightbend.akka.sample.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.sample.actors.SupervisingActor;

public class FailureHandlingExampleMain {

    public static void main(String[] args) {
        ActorSystem actorSystem = ActorSystem.create("failure-handling");
        ActorRef supervisingActor = actorSystem.actorOf(SupervisingActor.props(), "supervising-actor");
        supervisingActor.tell("failChild", ActorRef.noSender());
    }
}
