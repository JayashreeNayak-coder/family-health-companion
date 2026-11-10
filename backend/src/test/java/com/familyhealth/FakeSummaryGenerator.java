package com.familyhealth;

import com.familyhealth.ai.SummaryGenerator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Replaces the provider at its interface only. It keeps every request it receives, so tests can
 * check exactly what would have been sent to the provider (docs/07 section 1).
 */
public class FakeSummaryGenerator implements SummaryGenerator {

	private final Deque<Object> answers = new ArrayDeque<>();

	private final List<Request> requests = new ArrayList<>();

	private volatile boolean available = true;

	public synchronized void reset() {
		answers.clear();
		requests.clear();
		available = true;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	/** A {@link Result} to return, or a {@link RuntimeException} to throw. */
	public synchronized void willAnswer(Object... next) {
		for (Object answer : next) {
			answers.addLast(answer);
		}
	}

	public synchronized List<Request> requests() {
		return List.copyOf(requests);
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public String provider() {
		return "fake";
	}

	@Override
	public String model() {
		return "fake-model";
	}

	@Override
	public String promptVersion() {
		return "summaries-v1";
	}

	@Override
	public synchronized Result generate(Request request) {
		requests.add(request);
		Object answer = answers.pollFirst();
		if (answer instanceof RuntimeException failure) {
			throw failure;
		}
		if (answer == null) {
			throw new IllegalStateException("No scripted answer");
		}
		return (Result) answer;
	}

}
