package com.lightbend.akka.sample.actors;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.lightbend.akka.sample.protocol.DeviceProtocol;

import java.util.Optional;

public class Device extends AbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;

    final String deviceId;

    Optional<Double> lastTemperatureReading = Optional.empty();

    public Device(String groupId, String deviceId) {
        this.groupId = groupId;
        this.deviceId = deviceId;
    }

    public static Props props(String groupId, String deviceId) {
        return Props.create(Device.class, () -> new Device(groupId, deviceId));
    }

    @Override
    public void preStart() throws Exception {
        log.info("device actor {}-{} started", groupId, deviceId);
    }

    @Override
    public void postStop() throws Exception {
        log.info("device actor {}-{} stopped", groupId, deviceId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceProtocol.RequestTrackDevice.class, r-> {
                    if (this.groupId.equals(r.groupId) && this.deviceId.equals(r.deviceId)) {
                        getSender().tell(new DeviceProtocol.DeviceRegistered(), getSelf());
                    } else {
                        log.warning("Ignoring TrackDevice request for {}-{}. This actor is responsible for {}-{}", r.groupId, r.deviceId, this.groupId, this.deviceId);
                    }
                })
                .match(DeviceProtocol.RecordTemperature.class, r -> {
                    log.info("Recorded temperature reading {} with {}", r.getValue(), r.getRequestId());
                    lastTemperatureReading = Optional.of(r.getValue());
                    getSender().tell(new DeviceProtocol.TemperatureRecorded(r.getRequestId()), getSelf());
                })
                .match(DeviceProtocol.ReadTemperature.class, r -> {
                    getSender().tell(new DeviceProtocol.RespondTemperature(r.getRequestId(), lastTemperatureReading), getSelf());
                }).build();
    }
}
