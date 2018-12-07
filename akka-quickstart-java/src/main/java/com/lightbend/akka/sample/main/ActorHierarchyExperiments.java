package com.lightbend.akka.sample.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.sample.actors.PrintMyActorRefActor;

import java.io.IOException;

public class ActorHierarchyExperiments {

    public static void main(String[] args) throws IOException {
        ActorSystem actorSystem = ActorSystem.create("testSystem");

        ActorRef firstRef = actorSystem.actorOf(PrintMyActorRefActor.props(), "first-actor");
        System.out.println("First: " + firstRef);
        firstRef.tell("printit", ActorRef.noSender());

        System.out.println(">>> Press ENTER to exit <<<");

        try {
            System.in.read();
        } finally {
            actorSystem.terminate();
        }

    }


}


