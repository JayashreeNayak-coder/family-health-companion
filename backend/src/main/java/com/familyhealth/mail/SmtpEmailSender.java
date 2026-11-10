package com.familyhealth.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP through Spring Mail, Gmail by default (DECISIONS.md, D-028). Message text is never logged,
 * except by the development fallback when no mail account is set.
 */
@Component
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mail;

	private final String username;

	private final String from;

	private final boolean logWhenUnconfigured;

	private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

	SmtpEmailSender(JavaMailSender mail, @Value("${spring.mail.username:}") String username,
			@Value("${app.mail.from:}") String from,
			@Value("${app.mail.log-when-unconfigured:false}") boolean logWhenUnconfigured) {
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
			log.info("Email sending is on");
		}
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
			}
		});
	}

}
