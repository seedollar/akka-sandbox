package com.lightbend.akka.sample.main;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.lightbend.akka.sample.actors.IotSupervisor;

import java.io.IOException;

public class IotMain {

    public static void main(String[] args) throws IOException {
        ActorSystem actorSystem = ActorSystem.create("iot-system");

        try {
            ActorRef iotSupervisor = actorSystem.actorOf(IotSupervisor.props(), "iot-supervisor");
            System.out.println("Press ENTER to exit the system");
            System.in.read();
        } finally {
            actorSystem.terminate();
        }
    }
}
