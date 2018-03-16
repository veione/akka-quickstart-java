package com.lightbend.akka.sample;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.lightbend.akka.sample.DeviceGroup.DeviceNotAvailable;
import com.lightbend.akka.sample.DeviceGroup.TemperatureReading;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

public class DeviceGroupQuery extends AbstractActor {
	public static final class CollectionTimeout {

	}

	private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

	final Map<ActorRef, String> actorToDeviceId;
	final long requestId;
	final ActorRef requester;

	Cancellable queryTimeoutTimer;

	public DeviceGroupQuery(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester,
			FiniteDuration timeout) {
		this.actorToDeviceId = actorToDeviceId;
		this.requestId = requestId;
		this.requester = requester;

		queryTimeoutTimer = getContext().getSystem().scheduler().scheduleOnce(timeout, getSelf(),
				new CollectionTimeout(), getContext().dispatcher(), getSelf());
	}

	public static Props props(Map<ActorRef, String> actorToDeviceId, long requestId, ActorRef requester,
			FiniteDuration timeout) {
		return Props.create(DeviceGroupQuery.class, actorToDeviceId, requestId, requester, timeout);
	}

	@Override
	public void preStart() {
		for (ActorRef deviceActor : actorToDeviceId.keySet()) {
			getContext().watch(deviceActor);
			deviceActor.tell(new Device.ReadTemperature(0L), getSelf());
		}
	}

	@Override
	public void postStop() {
		queryTimeoutTimer.cancel();
	}

	@Override
	public Receive createReceive() {
		return waitingForReplies(new HashMap<>(), actorToDeviceId.keySet());
	}

	private Receive waitingForReplies(Map<String, TemperatureReading> repliesSoFar,
			Set<ActorRef> stillWaiting) {
		return receiveBuilder().match(Device.ResponseTemperature.class, r -> {
			ActorRef deviceActor = getSender();
			DeviceGroup.TemperatureReading reading = r.value
					.map(v -> (DeviceGroup.TemperatureReading) new DeviceGroup.Temperature(v))
					.orElse(new DeviceGroup.TemperatureNotAvailable());
			receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar);

		}).match(Terminated.class, t -> {
			receivedResponse(t.getActor(), new DeviceGroup.DeviceNotAvailable(), stillWaiting, repliesSoFar);
		}).match(CollectionTimeout.class, t -> {
			Map<String, DeviceGroup.TemperatureReading> replices = new HashMap<>(repliesSoFar);
			for (ActorRef deviceActor : stillWaiting) {
				String deviceId = actorToDeviceId.get(deviceActor);
				replices.put(deviceId, new DeviceGroup.DeviceTimedOut());
			}
			requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, replices), getSelf());
			getContext().stop(getSelf());
		}).build();
	}

	private void receivedResponse(ActorRef deviceActor, TemperatureReading reading, Set<ActorRef> stillWaiting,
			Map<String, TemperatureReading> repliesSoFar) {
		getContext().unwatch(deviceActor);
		String deviceId = actorToDeviceId.get(deviceActor);

		Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
		newStillWaiting.remove(deviceActor);

		Map<String, DeviceGroup.TemperatureReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
		newRepliesSoFar.put(deviceId, reading);
		if (newStillWaiting.isEmpty()) {
			requester.tell(new DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar), getSelf());
			getContext().stop(getSelf());
		} else {
			getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
		}
	}
}
