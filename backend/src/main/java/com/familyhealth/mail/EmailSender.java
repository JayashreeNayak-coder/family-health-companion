package com.familyhealth.mail;

/**
 * Sends plain-text email. Sending happens in the background: the caller never waits for the mail
 * server, and a failure is logged, never thrown. So a request takes the same time whether or not
 * an email goes out, which keeps account existence hidden.
 */
public interface EmailSender {

	void send(String to, String subject, String text);

}
