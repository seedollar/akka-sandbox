package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.akka.sample.protocol.DeviceProtocol;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeviceGroup extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;
    final Map<String, ActorRef> deviceIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToDeviceId = new HashMap<>();

    public static Props props(String groupId) {
        return Props.create(DeviceGroup.class, () -> new DeviceGroup(groupId));
    }

    public DeviceGroup(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public void preStart() throws Exception {
        log.info("DeviceGroup {} started", this.groupId);
    }

    @Override
    public void postStop() throws Exception {
        log.info("DeviceGroup {} stopped", this.groupId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceProtocol.RequestTrackDevice.class, this::onTrackDevice)
                .match(DeviceProtocol.RequestDeviceList.class, this::onDeviceList)
                .match(DeviceProtocol.RequestAllTemperatures.class, this::onAllTemperatures)
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    private void onTrackDevice(DeviceProtocol.RequestTrackDevice trackMsg) {
        if (this.groupId.equals(trackMsg.groupId)) {
            ActorRef deviceActor = deviceIdToActor.get(trackMsg.deviceId);
            if (deviceActor != null) {
                deviceActor.forward(trackMsg, getContext());
            } else {
                log.info("Creating device actor for {}", trackMsg.deviceId);
                deviceActor = getContext().actorOf(Device.props(this.groupId, trackMsg.deviceId), "device-" + trackMsg.deviceId);
                getContext().watch(deviceActor);
                actorToDeviceId.put(deviceActor, trackMsg.deviceId);
                deviceIdToActor.put(trackMsg.deviceId, deviceActor);
                deviceActor.forward(trackMsg, getContext());
            }
        } else {
            log.warning("Ignoring TrackDevice request for {}. The actor is responsible for {}.", groupId, this.groupId);
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef deviceActor = t.getActor();
        String deviceId = actorToDeviceId.get(deviceActor);
        log.info("Device actor for {} has been terminated", deviceId);
        actorToDeviceId.remove(deviceActor);
        deviceIdToActor.remove(deviceId);
    }

    private void onDeviceList(DeviceProtocol.RequestDeviceList request) {
        getSender().tell(new DeviceProtocol.ReplyDeviceList(request.getRequestId(), deviceIdToActor.keySet()), getSelf());
    }

    private void onAllTemperatures(DeviceProtocol.RequestAllTemperatures r) {
        Map<ActorRef, String> actorToDeviceIdCopy = new HashMap<>(this.actorToDeviceId); // Make defensive copy because collection is not thread safe.
        getContext().actorOf(DeviceGroupQuery.props(r.getRequestId(), getSender(), actorToDeviceIdCopy, FiniteDuration.apply(3, TimeUnit.SECONDS)));
    }
}
