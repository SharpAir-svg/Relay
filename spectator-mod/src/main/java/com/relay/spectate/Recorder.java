package com.relay.spectate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Ring buffer holding the last {@link #CAPACITY} ticks (20 s) of both fighters. */
public final class Recorder {
	public static final int CAPACITY = 20 * 20;

	public record Tick(Frame a, Frame b) {}

	private final ArrayDeque<Tick> buffer = new ArrayDeque<>(CAPACITY + 1);

	public void record(Frame a, Frame b) {
		buffer.addLast(new Tick(a, b));
		while (buffer.size() > CAPACITY) buffer.removeFirst();
	}

	public List<Tick> snapshot() {
		return new ArrayList<>(buffer);
	}

	public void clear() {
		buffer.clear();
	}
}
