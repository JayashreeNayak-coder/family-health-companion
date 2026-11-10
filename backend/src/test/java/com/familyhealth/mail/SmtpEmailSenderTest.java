package com.familyhealth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailSenderTest {

	/** Stands in for the mail server without opening a connection. */
	static class StubMailServer extends JavaMailSenderImpl {

		final CountDownLatch attempted = new CountDownLatch(1);

		final AtomicReference<SimpleMailMessage> sent = new AtomicReference<>();

		final AtomicInteger signInChecks = new AtomicInteger();

		final boolean refuse;

		StubMailServer(boolean refuse) {
			this.refuse = refuse;
		}

		@Override
		public void send(SimpleMailMessage message) {
			sent.set(message);
			attempted.countDown();
			if (refuse) {
				throw new MailAuthenticationException("refused");
			}
		}

		@Override
		public void testConnection() throws MessagingException {
			signInChecks.incrementAndGet();
			if (refuse) {
				throw new AuthenticationFailedException("refused");
			}
		}

	}

	@Test
	void sendsFromTheAccountWhenNoSenderIsSet() throws InterruptedException {
		StubMailServer server = new StubMailServer(false);
		new SmtpEmailSender(server, "sender@example.com", "", false).send("to@example.com", "Subject", "Body");

		assertThat(server.attempted.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(server.sent.get().getFrom()).isEqualTo("sender@example.com");
		assertThat(server.sent.get().getTo()).containsExactly("to@example.com");
	}

	@Test
	void aRefusedSignInNeverReachesTheCaller() throws InterruptedException {
		StubMailServer server = new StubMailServer(true);
		SmtpEmailSender sender = new SmtpEmailSender(server, "sender@example.com", "", true);

		assertThatCode(() -> sender.send("to@example.com", "Subject", "Body")).doesNotThrowAnyException();
		assertThat(server.attempted.await(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	void checksTheSignInOnceAtStartupOnlyWhenAnAccountIsSet() throws InterruptedException {
		StubMailServer configured = new StubMailServer(true);
		new SmtpEmailSender(configured, "sender@example.com", "", false);
		StubMailServer unconfigured = new StubMailServer(false);
		new SmtpEmailSender(unconfigured, "", "", true).send("to@example.com", "Subject", "Body");

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (configured.signInChecks.get() == 0 && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertThat(configured.signInChecks.get()).isEqualTo(1);
		assertThat(unconfigured.signInChecks.get()).isZero();
		assertThat(unconfigured.sent.get()).isNull();
	}

}
