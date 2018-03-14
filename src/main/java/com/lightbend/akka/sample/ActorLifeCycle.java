package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

public class ActorLifeCycle {

	public static void main(String[] args) {
		ActorSystem system = ActorSystem.create("actor-lifecycle");
		
		ActorRef first = system.actorOf(Props.create(StartStopActor1.class), "first");
		first.tell("stop", ActorRef.noSender());
	}
}

class StartStopActor1 extends AbstractActor {
	@Override
	public void preStart() throws Exception {
		System.out.println("first started");
		getContext().actorOf(Props.create(StartStopActor2.class), "second");
	}

	@Override
	public void postStop() throws Exception {
		System.out.println("first stopped");
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().matchEquals("stop", s -> {
			getContext().stop(getSelf());
		}).build();
	}
}

class StartStopActor2 extends AbstractActor {

	@Override
	public void preStart() throws Exception {
		System.out.println("second started");
	}

	@Override
	public void postStop() throws Exception {
		System.out.println("second stopped");
	}

	// Actor.emptyBehavior is a useful placeholder when we dont't
	// want to handle any messages in the actor.
	@Override
	public Receive createReceive() {
		return receiveBuilder().build();
	}

}
