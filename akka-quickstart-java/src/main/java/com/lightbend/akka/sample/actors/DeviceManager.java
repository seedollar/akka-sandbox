package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.akka.sample.protocol.DeviceProtocol;

import java.util.HashMap;
import java.util.Map;

public class DeviceManager extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private Map<String, ActorRef> groupIdToActor = new HashMap<>();
    private Map<ActorRef, String> actorToGroupId = new HashMap<>();

    public static Props props() {
        return Props.create(DeviceManager.class, DeviceManager::new);
    }

    @Override
    public void preStart() throws Exception {
        log.info("DeviceManager started");
    }

    @Override
    public void postStop() throws Exception {
        log.info("DeviceManager stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceProtocol.RequestTrackDevice.class, this::onTrackDevice)
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    private void onTrackDevice(DeviceProtocol.RequestTrackDevice trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.forward(trackMsg, getContext());
        } else {
            log.info("Creating device group actor for {}", groupId);
            ActorRef groupActor = getContext().actorOf(DeviceGroup.props(groupId), "group-" + groupId);
            getContext().watch(groupActor);
            groupActor.forward(trackMsg, getContext());
            groupIdToActor.put(groupId, groupActor);
            actorToGroupId.put(groupActor, groupId);
        }
    }

    private void onTerminated(Terminated terminatedMsg) {
        ActorRef groupActor = terminatedMsg.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log.info("Device group actor for {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }
}
