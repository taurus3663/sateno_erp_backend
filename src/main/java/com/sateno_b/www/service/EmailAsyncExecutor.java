package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.EmailEntity;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailAsyncExecutor {

    @Async
    public void execute(EmailEntity cfg, String to, String subject, String finalHtml) {
        TransportStrategy strategy = cfg.isSslSmtp() ?
                ((cfg.getPortSmtp() == 465) ? TransportStrategy.SMTPS : TransportStrategy.SMTP_TLS) : TransportStrategy.SMTP;

        try (Mailer mailer = MailerBuilder
                .withSMTPServer(cfg.getHostSmtp(), cfg.getPortSmtp(), cfg.getUsernameSmtp(), cfg.getPasswordSmtp())
                .withTransportStrategy(strategy)
                .buildMailer()) {

            Email email = EmailBuilder.startingBlank()
                    .from(cfg.getName(), cfg.getUsernameSmtp())
                    .to(to)
                    .withSubject(subject)
                    .withPlainText(subject)
                    .withHTMLText(finalHtml)
                    .buildEmail();

            mailer.sendMail(email);
            System.out.println("Имейлът е изпратен успешно асинхронно до: " + to);
        } catch (Exception e) {
            System.err.println("ASYNC SMTP ERROR: " + e.getMessage());
        }
    }
}
