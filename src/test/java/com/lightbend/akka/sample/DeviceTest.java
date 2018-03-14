package com.lightbend.akka.sample;

import static org.junit.Assert.*;

import java.util.Optional;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

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
}
