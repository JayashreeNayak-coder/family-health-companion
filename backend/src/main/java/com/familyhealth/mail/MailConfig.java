package com.familyhealth.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.json.JsonMapper;

/**
 * Brevo's HTTPS API when BREVO_API_KEY is set (the hosted demo, whose host blocks SMTP,
 * DECISIONS.md, D-033), and otherwise SMTP (Gmail by default, D-028).
 */
@Configuration(proxyBeanMethods = false)
class MailConfig {

	@Bean
	EmailSender emailSender(ObjectProvider<JavaMailSender> smtp, JsonMapper json,
			@Value("${app.mail.brevo.api-key:}") String brevoKey,
			@Value("${app.mail.brevo.base-url:https://api.brevo.com}") String brevoUrl,
			@Value("${spring.mail.username:}") String username, @Value("${app.mail.from:}") String from,
			@Value("${app.mail.log-when-unconfigured:false}") boolean logWhenUnconfigured) {
		if (brevoKey != null && !brevoKey.isBlank()) {
			BrevoEmailSender brevo = new BrevoEmailSender(json, brevoUrl, brevoKey, from);
			brevo.reportStatus();
			return brevo;
		}
		return new SmtpEmailSender(smtp.getObject(), username, from, logWhenUnconfigured);
	}

}
