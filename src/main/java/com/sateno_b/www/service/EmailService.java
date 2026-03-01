package com.sateno_b.www.service;

import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.repository.EmailRepository;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.stereotype.Service;

import java.util.Properties;

@RequiredArgsConstructor
//@Slf4j
@Service
public class EmailService {


    private final EmailRepository emailRepository;

    public void sendEmail() {

    }

    public void readEmail() {

    }

    public boolean testConnection(Long id) {
        EmailEntity cfg = emailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Config not found"));

        // Използвай SMTP флаговете!
        TransportStrategy strategy = (!cfg.isSslSmtp()) ? TransportStrategy.SMTP_TLS : TransportStrategy.SMTP;
        System.out.println(cfg.getHostSmtp());
        try {
            Mailer mailer = MailerBuilder
                    .withSMTPServer(
                            cfg.getHostSmtp(),
                            cfg.getPortSmtp(),
                            cfg.getUsernameSmtp(),
                           cfg.getPasswordSmtp() // Тестваме с твърда парола
                    )
                    .withTransportStrategy(strategy)
                    .withSessionTimeout(10000) // Увеличи малко времето за бавни сървъри
                    .withDebugLogging(true)
                    .buildMailer();

            mailer.testConnection();
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Грешка при връзка: " + e.getMessage());
        }
    }

    public boolean testIncomingConnection(Long id) {

        EmailEntity cfg = emailRepository.findById(id).orElseThrow();

        String protocol = cfg.getEmailType().name().toLowerCase(); // imap или pop3

        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", cfg.getHost());
        props.put("mail." + protocol + ".port", String.valueOf(cfg.getPort()));
        props.put("mail." + protocol + ".auth", "true");

        if (cfg.isSsl()) {
            props.put("mail." + protocol + ".ssl.enable", "true");
        }

        Session session = Session.getInstance(props);

        try (Store store = session.getStore(protocol)) {
            store.connect(cfg.getHost(), cfg.getUsername(), cfg.getPassword());
            return true;
        } catch (Exception e) {
            throw new RuntimeException(protocol.toUpperCase() + " Error: " + e.getMessage());
        }
    }


}
