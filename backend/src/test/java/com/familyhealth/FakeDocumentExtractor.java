package com.familyhealth;

import com.familyhealth.ai.DocumentExtractor;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Replaces the provider at its interface only (docs/07 section 1), so the queue, draft, review,
 * and record creation all run for real. Each call takes the next scripted answer.
 */
public class FakeDocumentExtractor implements DocumentExtractor {

	private final Deque<Object> answers = new ArrayDeque<>();

	private volatile boolean available = true;

	private volatile int calls;

	public void reset() {
		answers.clear();
		available = true;
		calls = 0;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	/** A {@link Result} to return, or a {@link RuntimeException} to throw. */
	public void willAnswer(Object... next) {
		for (Object answer : next) {
			answers.addLast(answer);
		}
	}

	public int calls() {
		return calls;
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
		return "extraction-v1";
	}

	@Override
	public Result extract(byte[] content, String contentType) {
		calls++;
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
