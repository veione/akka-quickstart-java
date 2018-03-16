package com.lightbend.akka.sample;

import java.util.LinkedList;

public class TestList {

	public static void main(String[] args) {
		LinkedList<Team> matchTeams = new LinkedList<>();
		matchTeams.add(new Team(100, 1));
		matchTeams.add(new Team(100, 1));
		matchTeams.add(new Team(200, 2));
		matchTeams.add(new Team(200, 3));
		matchTeams.add(new Team(200, 1));

		System.out.println(matchTeams.stream().mapToInt(Team::size).sum());
		if (matchTeams.stream().anyMatch(t -> t.time >= 200) && matchTeams.stream().mapToInt(Team::size).sum() > 1) {
			System.out.println("hello");
		}
	}

	public static class Team {
		public final long time;
		public final int num;

		public Team(long time, int num) {
			this.time = time;
			this.num = num;
		}
		
		public int size() {
			return num;
		}
	}
}
