package com.lightbend.akka.sample;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import com.lightbend.akka.sample.actors.Device;
import com.lightbend.akka.sample.actors.DeviceGroup;
import com.lightbend.akka.sample.actors.DeviceGroupQuery;
import com.lightbend.akka.sample.protocol.DeviceProtocol;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeviceTest {

    static ActorSystem actorSystem;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("DeviceTest");
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem);
        actorSystem = null;
    }

    @Test
    public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
        TestKit testKit = new TestKit(actorSystem);
        ActorRef deviceActor = actorSystem.actorOf(Device.props("groupId", "deviceId"));
        deviceActor.tell(new DeviceProtocol.ReadTemperature(53L), testKit.getRef());
        DeviceProtocol.RespondTemperature response = testKit.expectMsgClass(DeviceProtocol.RespondTemperature.class);
        Assert.assertEquals(53L, response.getRequestId());
        Assert.assertEquals(Optional.empty(), response.getValue());
    }

    @Test
    public void testReplyWithLatestTemperatureReading() {
        TestKit testProbe = new TestKit(actorSystem);
        ActorRef deviceActor = actorSystem.actorOf(Device.props("groupId", "deviceId"));
        deviceActor.tell(new DeviceProtocol.RecordTemperature(345L, 32.3D), testProbe.getRef());
        Assert.assertEquals(345L, testProbe.expectMsgClass(DeviceProtocol.TemperatureRecorded.class).getRequestId());

        deviceActor.tell(new DeviceProtocol.ReadTemperature(346L), testProbe.getRef());
        DeviceProtocol.RespondTemperature response1 = testProbe.expectMsgClass(DeviceProtocol.RespondTemperature.class);
        Assert.assertEquals(346L, response1.getRequestId());
        Assert.assertEquals(Optional.of(32.3D), response1.getValue());

        deviceActor.tell(new DeviceProtocol.RecordTemperature(347L, 33.7D), testProbe.getRef());
        Assert.assertEquals(347L, testProbe.expectMsgClass(DeviceProtocol.TemperatureRecorded.class).getRequestId());

        deviceActor.tell(new DeviceProtocol.ReadTemperature(348L), testProbe.getRef());
        DeviceProtocol.RespondTemperature response2 = testProbe.expectMsgClass(DeviceProtocol.RespondTemperature.class);
        Assert.assertEquals(348L, response2.getRequestId());
        Assert.assertEquals(Optional.of(33.7D), response2.getValue());
    }

    @Test
    public void testReplyToRegistrationRequests() {
        TestKit testProbe = new TestKit(actorSystem);
        ActorRef deviceActor = actorSystem.actorOf(Device.props("groupId", "deviceId"));
        deviceActor.tell(new DeviceProtocol.RequestTrackDevice("wrongGroupId", "deviceId"), testProbe.getRef());
        testProbe.expectNoMessage();

        deviceActor.tell(new DeviceProtocol.RequestTrackDevice("groupId", "wrongDeviceId"), testProbe.getRef());
        testProbe.expectNoMessage();
    }

    @Test
    public void testRegisterDeviceActor() {
        TestKit testProbe = new TestKit(actorSystem);
        ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device1"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);
        ActorRef deviceActor1 = testProbe.getLastSender();

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device2"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);
        ActorRef deviceActor2 = testProbe.getLastSender();
        Assert.assertNotEquals(deviceActor1, deviceActor2);

        deviceActor1.tell(new DeviceProtocol.RecordTemperature(1L, 22.4D), testProbe.getRef());
        Assert.assertEquals(1L, testProbe.expectMsgClass(DeviceProtocol.TemperatureRecorded.class).getRequestId());
        deviceActor2.tell(new DeviceProtocol.RecordTemperature(2L, 45.21D), testProbe.getRef());
        Assert.assertEquals(2L, testProbe.expectMsgClass(DeviceProtocol.TemperatureRecorded.class).getRequestId());
    }

    @Test
    public void testIgnoreRequestsForWrongGroupId() {
        TestKit testProbe = new TestKit(actorSystem);
        ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"), "deviceGroup");
        groupActor.tell(new DeviceProtocol.RequestTrackDevice("wrongGroup", "deviceId"), testProbe.getRef());
        testProbe.expectNoMessage();
    }

    @Test
    public void testReturnSameActorForSameDeviceId() {
        TestKit testProbe = new TestKit(actorSystem);

        ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device1"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);
        ActorRef deviceActor1 = testProbe.getLastSender();

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device1"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);
        ActorRef deviceActor2 = testProbe.getLastSender();

        Assert.assertEquals(deviceActor1, deviceActor2);
    }

    @Test
    public void testListActiveDevices() {
        TestKit testProbe = new TestKit(actorSystem);

        ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device1"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device2"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);

        groupActor.tell(new DeviceProtocol.RequestDeviceList(1L), testProbe.getRef());
        DeviceProtocol.ReplyDeviceList replyDeviceList = testProbe.expectMsgClass(DeviceProtocol.ReplyDeviceList.class);
        Assert.assertEquals(1L, replyDeviceList.getRequestId());
        Assert.assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), replyDeviceList.getIds());
    }

    @Test
    public void testListActiveDevicesAfterOneShutsDown() {
        TestKit testProbe = new TestKit(actorSystem);
        ActorRef groupActor = actorSystem.actorOf(DeviceGroup.props("group"), "deviceGroup");

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device1"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);
        ActorRef deviceActor1ToShutdown = testProbe.getLastSender();

        groupActor.tell(new DeviceProtocol.RequestTrackDevice("group", "device2"), testProbe.getRef());
        testProbe.expectMsgClass(DeviceProtocol.DeviceRegistered.class);

        groupActor.tell(new DeviceProtocol.RequestDeviceList(22L), testProbe.getRef());
        DeviceProtocol.ReplyDeviceList replyDeviceList = testProbe.expectMsgClass(DeviceProtocol.ReplyDeviceList.class);
        Assert.assertEquals(22L, replyDeviceList.getRequestId());
        Assert.assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), replyDeviceList.getIds());

        testProbe.watch(deviceActor1ToShutdown);
        deviceActor1ToShutdown.tell(PoisonPill.getInstance(), ActorRef.noSender());
        testProbe.expectTerminated(deviceActor1ToShutdown);

        testProbe.awaitAssert(() -> {
            groupActor.tell(new DeviceProtocol.RequestDeviceList(25L), testProbe.getRef());
            DeviceProtocol.ReplyDeviceList replyDeviceList2 = testProbe.expectMsgClass(DeviceProtocol.ReplyDeviceList.class);
            Assert.assertEquals(25L, replyDeviceList2.getRequestId());
            Assert.assertEquals(Stream.of("device2").collect(Collectors.toSet()), replyDeviceList2.getIds());
            return null;
        });
    }

    @Test
    public void testReturnTemperatureValueForWorkingDevices() {
        TestKit requester = new TestKit(actorSystem);

        TestKit device1 = new TestKit(actorSystem);
        TestKit device2 = new TestKit(actorSystem);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = actorSystem.actorOf(DeviceGroupQuery.props(111L, requester.getRef(), actorToDeviceId, FiniteDuration.apply(3, TimeUnit.SECONDS)));

        Assert.assertEquals(0L, device1.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());
        Assert.assertEquals(0L, device2.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());

        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());

        DeviceProtocol.RespondAllTemperatures respondAllTemperatures = requester.expectMsgClass(DeviceProtocol.RespondAllTemperatures.class);
        Assert.assertEquals(111L, respondAllTemperatures.getRequestId());

        Map<String, DeviceProtocol.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceProtocol.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceProtocol.Temperature(2.0));

        Assert.assertEquals(expectedTemperatures, respondAllTemperatures.getTemperatures());
    }

    @Test
    public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
        TestKit requester = new TestKit(actorSystem);

        TestKit device1 = new TestKit(actorSystem);
        TestKit device2 = new TestKit(actorSystem);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = actorSystem.actorOf(DeviceGroupQuery.props(222L, requester.getRef(), actorToDeviceId, FiniteDuration.apply(3, TimeUnit.SECONDS)));

        Assert.assertEquals(0L, device1.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());
        Assert.assertEquals(0L, device2.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());

        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.empty()), device1.getRef());
        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(3.0)), device2.getRef());

        DeviceProtocol.RespondAllTemperatures response = requester.expectMsgClass(DeviceProtocol.RespondAllTemperatures.class);
        Assert.assertEquals(222L, response.getRequestId());

        Map<String, DeviceProtocol.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", DeviceProtocol.TemperatureNotAvailable.INSTANCE);
        expectedTemperatures.put("device2", new DeviceProtocol.Temperature(3.0));

        Assert.assertEquals(expectedTemperatures, response.getTemperatures());
    }

    @Test
    public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
        TestKit requester = new TestKit(actorSystem);

        TestKit device1 = new TestKit(actorSystem);
        TestKit device2 = new TestKit(actorSystem);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = actorSystem.actorOf(DeviceGroupQuery.props(333L, requester.getRef(), actorToDeviceId, FiniteDuration.apply(3, TimeUnit.SECONDS)));

        Assert.assertEquals(0L, device1.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());
        Assert.assertEquals(0L, device2.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());

        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(4.0)), device1.getRef());
        device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

        DeviceProtocol.RespondAllTemperatures respondAllTemperatures = requester.expectMsgClass(DeviceProtocol.RespondAllTemperatures.class);
        Assert.assertEquals(333L, respondAllTemperatures.getRequestId());

        Map<String, DeviceProtocol.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceProtocol.Temperature(4.0));
        expectedTemperatures.put("device2", DeviceProtocol.DeviceNotAvailable.INSTANCE);

        Assert.assertEquals(expectedTemperatures, respondAllTemperatures.getTemperatures());
    }

    @Test
    public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
        TestKit requester = new TestKit(actorSystem);

        TestKit device1 = new TestKit(actorSystem);
        TestKit device2 = new TestKit(actorSystem);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = actorSystem.actorOf(DeviceGroupQuery.props(444L, requester.getRef(), actorToDeviceId, FiniteDuration.apply(3, TimeUnit.SECONDS)));

        Assert.assertEquals(0L, device1.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());
        Assert.assertEquals(0L, device2.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());

        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(5.5)), device1.getRef());
        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(6.3)), device2.getRef());
        device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

        DeviceProtocol.RespondAllTemperatures respondAllTemperatures = requester.expectMsgClass(DeviceProtocol.RespondAllTemperatures.class);
        Assert.assertEquals(444L, respondAllTemperatures.getRequestId());

        Map<String, DeviceProtocol.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceProtocol.Temperature(5.5));
        expectedTemperatures.put("device2", new DeviceProtocol.Temperature(6.3));

        Assert.assertEquals(expectedTemperatures, respondAllTemperatures.getTemperatures());
    }

    @Test
    public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
        TestKit requester = new TestKit(actorSystem);

        TestKit device1 = new TestKit(actorSystem);
        TestKit device2 = new TestKit(actorSystem);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = actorSystem.actorOf(DeviceGroupQuery.props(555L, requester.getRef(), actorToDeviceId, FiniteDuration.apply(1, TimeUnit.SECONDS)));

        Assert.assertEquals(0L, device1.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());
        Assert.assertEquals(0L, device2.expectMsgClass(DeviceProtocol.ReadTemperature.class).getRequestId());

        queryActor.tell(new DeviceProtocol.RespondTemperature(0L, Optional.of(7.2)), device1.getRef());

        DeviceProtocol.RespondAllTemperatures respondAllTemperatures = requester.expectMsgClass(DeviceProtocol.RespondAllTemperatures.class);
        Assert.assertEquals(555L, respondAllTemperatures.getRequestId());

        Map<String, DeviceProtocol.TemperatureReading> expectedTemperatures = new HashMap<>();

        expectedTemperatures.put("device1", new DeviceProtocol.Temperature(7.2));
        expectedTemperatures.put("device2", DeviceProtocol.DeviceTimedOut.INSTANCE);

        Assert.assertEquals(expectedTemperatures, respondAllTemperatures.getTemperatures());

    }
}
