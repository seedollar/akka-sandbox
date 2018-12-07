package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.dispatch.sysmsg.Terminate;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.akka.sample.protocol.DeviceProtocol;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeviceGroupQuery extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(long requestId, ActorRef requester, Map<ActorRef, String> actorToDeviceId, FiniteDuration timeout) {
        return Props.create(DeviceGroupQuery.class, () -> new DeviceGroupQuery(requestId, requester, actorToDeviceId, timeout));
    }

    final Map<ActorRef, String> actorToDeviceId;
    final long requestId;
    final ActorRef requester;

    Cancellable queryTimeoutTimer;

    public DeviceGroupQuery(long requestId, ActorRef requester, Map<ActorRef, String> actorToDeviceId, FiniteDuration timeout) {
        this.requestId = requestId;
        this.requester = requester;
        this.actorToDeviceId = actorToDeviceId;

        this.queryTimeoutTimer = getContext().getSystem().scheduler()
                .scheduleOnce(timeout, getSelf(), new DeviceProtocol.CollectionTimeout(), getContext().dispatcher(), getSelf());
    }

    @Override
    public void preStart() throws Exception {
        this.actorToDeviceId.keySet().forEach(deviceActor -> {
            getContext().watch(deviceActor);
            deviceActor.tell(new DeviceProtocol.ReadTemperature(0L), getSelf());
        });
    }

    @Override
    public void postStop() throws Exception {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
    }

    public Receive waitingForReplies(Map<String, DeviceProtocol.TemperatureReading> repliesSoFar, Set<ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(DeviceProtocol.RespondTemperature.class, r -> {
                    ActorRef deviceActor = getSender();
                    DeviceProtocol.TemperatureReading reading = r.getValue()
                            .map(v -> (DeviceProtocol.TemperatureReading) new DeviceProtocol.Temperature(v))
                            .orElse(DeviceProtocol.TemperatureNotAvailable.INSTANCE);
                    receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);
                })
                .match(Terminated.class, t -> {
                    receivedResponse(t.getActor(), DeviceProtocol.DeviceNotAvailable.INSTANCE, stillWaiting, repliesSoFar);
                })
                .match(DeviceProtocol.CollectionTimeout.class, r -> {
                    Map<String, DeviceProtocol.TemperatureReading> replies = new HashMap<>(repliesSoFar);
                    stillWaiting.forEach(deviceActor -> {
                        String deviceId = actorToDeviceId.get(deviceActor);
                        replies.put(deviceId, DeviceProtocol.DeviceTimedOut.INSTANCE);
                    });
                    requester.tell(new DeviceProtocol.RespondAllTemperatures(requestId, replies), getSelf());
                    getContext().stop(getSelf());
                })
                .build();
    }

    public void receivedResponse(ActorRef deviceActor, DeviceProtocol.TemperatureReading reading, Set<ActorRef> stillWaiting, Map<String, DeviceProtocol.TemperatureReading> repliesSoFar) {
        getContext().unwatch(deviceActor); // Ensures that we don't process any more responses for this device actor
        String deviceId = actorToDeviceId.get(deviceActor);

        Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
        newStillWaiting.remove(deviceActor);

        Map<String, DeviceProtocol.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
        newRepliesSoFar.put(deviceId, reading);
        if (newStillWaiting.isEmpty()) {
            requester.tell(new DeviceProtocol.RespondAllTemperatures(requestId, newRepliesSoFar), getSelf());
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}
