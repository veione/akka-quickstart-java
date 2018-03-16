package com.lightbend.akka.sample;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.lightbend.akka.sample.DeviceGroup.ReplyDeviceList;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public class DeviceTest {
	static ActorSystem system;

	@BeforeClass
	public static void before() {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void after() {
		system.terminate();
	}

	@Test
	public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));
		deviceActor.tell(new Device.ReadTemperature(42L), probe.getRef());
		Device.ResponseTemperature response = probe.expectMsgClass(Device.ResponseTemperature.class);
		assertEquals(42L, response.requestId);
		assertEquals(Optional.empty(), response.value);
	}

	@Test
	public void testReplyWithLatestTemperatureReading() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("groupId", "device"));

		deviceActor.tell(new Device.RecordTemperature(1L, 24.0), probe.getRef());
		assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

		deviceActor.tell(new Device.ReadTemperature(2L), probe.getRef());
		Device.ResponseTemperature response1 = probe.expectMsgClass(Device.ResponseTemperature.class);
		assertEquals(2L, response1.requestId);
		assertEquals(Optional.of(24.0), response1.value);

		deviceActor.tell(new Device.RecordTemperature(3L, 55.0), probe.getRef());
		assertEquals(3L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);

		deviceActor.tell(new Device.ReadTemperature(4L), probe.getRef());
		Device.ResponseTemperature response2 = probe.expectMsgClass(Device.ResponseTemperature.class);
		assertEquals(4L, response2.requestId);
		assertEquals(Optional.of(55.0), response2.value);
	}

	@Test
	public void testReplyToRegistratioinRequests() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

		deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "device"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		assertEquals(deviceActor, probe.getLastSender());
	}

	@Test
	public void testIgnoreWrongRegistrationRequests() {
		TestKit probe = new TestKit(system);
		ActorRef deviceActor = system.actorOf(Device.props("group", "device"));

		deviceActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device"), probe.getRef());
		probe.expectNoMsg();

		deviceActor.tell(new DeviceManager.RequestTrackDevice("group", "wrongDevice"), probe.getRef());
		probe.expectNoMsg();
	}

	@Test
	public void testRegisterDeviceActor() {
		TestKit probe = new TestKit(system);
		ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef deviceActor1 = probe.getLastSender();

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef deviceActor2 = probe.getLastSender();
		assertNotEquals(deviceActor1, deviceActor2);

		// Check that the device actors are working
		deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
		assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
		deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
		assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
	}

	@Test
	public void testReturnSameActorForSameDeviceId() {
		TestKit probe = new TestKit(system);
		ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef deviceActor1 = probe.getLastSender();

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef deviceActor2 = probe.getLastSender();
		assertEquals(deviceActor1, deviceActor2);
	}

	@Test
	public void testListActiveDevices() {
		TestKit probe = new TestKit(system);
		ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

		groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
		DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
		assertEquals(0L, reply.requestId);
		assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);
	}

	@Test
	public void testListActiveDevicesAfterOneShutDown() {
		TestKit probe = new TestKit(system);
		ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef toShutDown = probe.getLastSender();

		groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

		groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
		DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
		assertEquals(0L, reply.requestId);
		assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

		probe.watch(toShutDown);
		toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
		probe.expectTerminated(toShutDown);

		// using awaitAssert to retry because it might take longer for the
		// groupActor
		// to see The Terminated, that order is undefined
		probe.awaitAssert(() -> {
			groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.getRef());
			DeviceGroup.ReplyDeviceList r = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
			assertEquals(1L, r.requestId);
			assertEquals(Stream.of("device2").collect(Collectors.toSet()), r.ids);
			return null;
		});
	}

	@Test
	public void testRegisterGroupOnDeviceManager() {
		TestKit probe = new TestKit(system);
		ActorRef deviceManagerActor = system.actorOf(DeviceManager.props());

		deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

		deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("group2", "device2"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

		deviceManagerActor.tell(new DeviceManager.RequestTrackDevice("group1", "device1"), probe.getRef());
		probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		ActorRef toShutdown = probe.getLastSender();

		probe.watch(toShutdown);
		toShutdown.tell(PoisonPill.getInstance(), ActorRef.noSender());
		probe.expectTerminated(toShutdown);
	}

	@Test
	public void testReturnTemperatureValueForWorkingDevices() {
		TestKit requester = new TestKit(system);

		TestKit device1 = new TestKit(system);
		TestKit device2 = new TestKit(system);

		Map<ActorRef, String> actorToDeviceId = new HashMap<>();
		actorToDeviceId.put(device1.getRef(), "device1");
		actorToDeviceId.put(device2.getRef(), "device2");

		ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(actorToDeviceId, 1L, requester.getRef(),
				new FiniteDuration(3, TimeUnit.SECONDS)));

		assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
		assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(1.0)), device1.getRef());
		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(2.0)), device2.getRef());

		DeviceGroup.RespondAllTemperatures response = requester
				.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
		assertEquals(1L, response.requestId);

		Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
		expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
		expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

		// assertEqualTemperatures(expectedTemperatures, response.temperatures);
	}

	@Test
	public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
		TestKit requester = new TestKit(system);

		TestKit device1 = new TestKit(system);
		TestKit device2 = new TestKit(system);

		Map<ActorRef, String> actorToDeviceId = new HashMap<>();
		actorToDeviceId.put(device1.getRef(), "device1");
		actorToDeviceId.put(device2.getRef(), "device2");

		ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(actorToDeviceId, 1L, requester.getRef(),
				new FiniteDuration(3, TimeUnit.SECONDS)));

		assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
		assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

		queryActor.tell(new Device.ResponseTemperature(0L, Optional.empty()), device1.getRef());
		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(2.0)), device2.getRef());

		DeviceGroup.RespondAllTemperatures response = requester
				.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
		assertEquals(1L, response.requestId);

		Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
		expectedTemperatures.put("device1", new DeviceGroup.TemperatureNotAvailable());
		expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

		// assertEqualsTemperatures(expectedTemperatures,
		// response.temperatures);
	}

	@Test
	public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
		TestKit requester = new TestKit(system);

		TestKit device1 = new TestKit(system);
		TestKit device2 = new TestKit(system);

		Map<ActorRef, String> actorToDeviceId = new HashMap<>();
		actorToDeviceId.put(device1.getRef(), "device1");
		actorToDeviceId.put(device2.getRef(), "device2");

		ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(actorToDeviceId, 1L, requester.getRef(),
				new FiniteDuration(3, TimeUnit.SECONDS)));

		assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
		assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(1.0)), device1.getRef());
		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(2.0)), device2.getRef());
		device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

		DeviceGroup.RespondAllTemperatures response = requester
				.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
		assertEquals(1L, response.requestId);

		Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
		expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
		expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

		// assertEqualsTemperatures(expectedTemperatures,
		// response.temperatures);
	}

	@Test
	public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
		TestKit requester = new TestKit(system);

		TestKit device1 = new TestKit(system);
		TestKit device2 = new TestKit(system);

		Map<ActorRef, String> actorToDeviceId = new HashMap<>();
		actorToDeviceId.put(device1.getRef(), "device1");
		actorToDeviceId.put(device2.getRef(), "device2");

		ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(actorToDeviceId, 1L, requester.getRef(),
				new FiniteDuration(3, TimeUnit.SECONDS)));

		assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
		assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

		queryActor.tell(new Device.ResponseTemperature(0L, Optional.of(1.0)), device1.getRef());

		DeviceGroup.RespondAllTemperatures response = requester
				.expectMsgClass(FiniteDuration.create(5, TimeUnit.SECONDS), DeviceGroup.RespondAllTemperatures.class);
		assertEquals(1L, response.requestId);
		
		Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
		expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
		expectedTemperatures.put("device2", new DeviceGroup.DeviceTimedOut());
		
		//assertEqualsTemperatures(expectedTemperatures, response.temperatures);
	}
	
	@Test
	public void testCollectTemperaturesFromAllActiveDevices() {
		TestKit probe = new TestKit(system);
		  ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

		  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
		  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		  ActorRef deviceActor1 = probe.getLastSender();

		  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
		  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		  ActorRef deviceActor2 = probe.getLastSender();

		  groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device3"), probe.getRef());
		  probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
		  ActorRef deviceActor3 = probe.getLastSender();

		  // Check that the device actors are working
		  deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
		  assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
		  deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
		  assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
		  // No temperature for device 3

		  groupActor.tell(new DeviceGroup.RequestAllTemperatures(0L), probe.getRef());
		  DeviceGroup.RespondAllTemperatures response = probe.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
		  assertEquals(0L, response.requestId);

		  Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
		  expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
		  expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));
		  expectedTemperatures.put("device3", new DeviceGroup.TemperatureNotAvailable());
		
		//assertEqualsTemperatures(expectedTemperatures, response.temperatures);
	}
}
