package com.familyhealth.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP through Spring Mail, Gmail by default (DECISIONS.md, D-028). Message text is never logged,
 * except by the development fallback when no mail account is set. Chosen by {@link MailConfig}.
 */
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mail;

	private final String username;

	private final String from;

	private final boolean logWhenUnconfigured;

	private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

	SmtpEmailSender(JavaMailSender mail, String username, String from, boolean logWhenUnconfigured) {
		this.mail = mail;
		this.username = username;
		this.from = from == null || from.isBlank() ? username : from;
		this.logWhenUnconfigured = logWhenUnconfigured;
		// Says at startup where emails go. No address or password is logged.
		if (username == null || username.isBlank()) {
			log.info(logWhenUnconfigured ? "Email sending is off: MAIL_USERNAME is not set, so emails are written to this log"
					: "Email sending is off: MAIL_USERNAME is not set");
		}
		else if (!username.contains("@")) {
			log.warn("MAIL_USERNAME is not an email address, so the mail server will refuse to sign in");
		}
		else {
			log.info("Email sending is on. Checking the mail server sign-in in the background");
			checkSignIn();
		}
	}

	/**
	 * Signs in to the mail server once, without sending anything, so wrong credentials show up at
	 * startup rather than as a reset email that silently never arrives. Never delays startup.
	 */
	private void checkSignIn() {
		if (!(mail instanceof JavaMailSenderImpl impl)) {
			return;
		}
		background.execute(() -> {
			try {
				impl.testConnection();
				log.info("Mail server sign-in works");
			}
			catch (AuthenticationFailedException ex) {
				log.warn("The mail server refused MAIL_USERNAME and MAIL_PASSWORD, so no email will be sent. "
						+ "For Gmail, MAIL_PASSWORD must be a 16-letter app password, not the account password");
			}
			catch (MessagingException ex) {
				log.warn("Could not reach the mail server: {}", ex.getClass().getSimpleName());
			}
		});
	}

	@Override
	public void send(String to, String subject, String text) {
		if (username == null || username.isBlank()) {
			if (logWhenUnconfigured) {
				log.warn("Mail is not configured. Development copy of the email \"{}\":\n{}", subject, text);
			}
			else {
				log.warn("Mail is not configured, so an email was not sent");
			}
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		background.execute(() -> {
			try {
				mail.send(message);
			}
			catch (MailException ex) {
				// The class only: messages can quote addresses.
				log.warn("Email could not be sent: {}{}", ex.getClass().getSimpleName(),
						ex instanceof MailAuthenticationException
								? ". The mail server refused MAIL_USERNAME and MAIL_PASSWORD (for Gmail, use a 16-letter app password)"
								: "");
				if (logWhenUnconfigured) {
					// Development only, as when no account is set: the flow can still be finished locally.
					log.warn("Development copy of the unsent email \"{}\":\n{}", subject, text);
				}
			}
		});
	}

}
