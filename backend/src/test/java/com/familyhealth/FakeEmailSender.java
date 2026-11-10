package com.familyhealth;

import com.familyhealth.mail.EmailSender;
import java.util.ArrayList;
import java.util.List;

/** Keeps every email instead of sending it, so tests can read the reset link. */
public class FakeEmailSender implements EmailSender {

	public record Sent(String to, String subject, String text) {
	}

	private final List<Sent> sent = new ArrayList<>();

	public synchronized void reset() {
		sent.clear();
	}

	public synchronized List<Sent> sentTo(String to) {
		return sent.stream().filter(message -> message.to().equals(to)).toList();
	}

	@Override
	public synchronized void send(String to, String subject, String text) {
		sent.add(new Sent(to, subject, text));
	}

}
