package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.EmailSendRequest;
import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.enums.EmailDirection;
import com.sateno_b.www.model.repository.EmailLogRepository;
import com.sateno_b.www.model.repository.EmailRepository;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import org.hibernate.service.spi.ServiceException;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.UUID;

@RequiredArgsConstructor
//@Slf4j
@Service
public class EmailService {


    private final EmailRepository emailRepository;
    private final EmailLogRepository emailLogRepository;
    @Value("${app.base-url}")
    private String baseUrl;

    public EmailLogEntity sendEmail(EmailSendRequest request) {
        EmailEntity cfg = emailRepository.findById(request.getConfigId())
                .orElseThrow(() -> new RuntimeException("Config not found"));

        String seenKey = UUID.randomUUID().toString();
        String trackingPixel = "<img src=\"" + baseUrl + "/erp/email/seen/" + seenKey + "\" width=\"1\" height=\"1\" style=\"display:none\">";

        String content = request.getContent();
        String fullBody = content + trackingPixel;

        TransportStrategy strategy = TransportStrategy.SMTP;
        if (cfg.isSslSmtp()) {
            strategy = (cfg.getPortSmtp() == 465) ? TransportStrategy.SMTPS : TransportStrategy.SMTP_TLS;
        }

        try (Mailer mailer = MailerBuilder
                .withSMTPServer(cfg.getHostSmtp(), cfg.getPortSmtp(), cfg.getUsernameSmtp(), cfg.getPasswordSmtp())
                .withTransportStrategy(strategy)
                .buildMailer()) {


            System.out.println("Email sent successfully to " + request.getTo());
            EmailLogEntity emailLogEntity = new EmailLogEntity();
            emailLogEntity.setSubject(request.getSubject());
            emailLogEntity.setConfig(cfg);
            emailLogEntity.setDirection(EmailDirection.SENT);
//            emailLogEntity.setPrivateConfirmKey(request.getConfirmKey());
            emailLogEntity.setPrivateSeenKey(seenKey);
            emailLogEntity.setSender(cfg.getName());
            emailLogEntity.setRecipient(request.getTo());
            emailLogEntity.setSeen(false);
            emailLogEntity.setConfirmed(false);

            if(request.isGenConfirm()) {
                String confirmKey = UUID.randomUUID().toString();
                String confirmUrl = baseUrl + "/erp/email/confirm/" + confirmKey;

                String buttonHtml = """
    <div style="text-align: center; margin: 20px 0;">
        <a href="%s"
           style="background-color: #3B82F6; 
                  color: white; 
                  padding: 12px 25px; 
                  text-decoration: none; 
                  border-radius: 5px; 
                  font-weight: bold; 
                  display: inline-block;
                  font-family: Arial, sans-serif;">
            ПОТВЪРДИ ПОРЪЧКАТА
        </a>
    </div>
    """.formatted(confirmUrl);


                    fullBody += buttonHtml;

                emailLogEntity.setPrivateConfirmKey(confirmKey);

            }

            if (cfg.getSignature() != null) {
                fullBody += "<br><br>" + cfg.getSignature();
            }
            emailLogEntity.setBody(fullBody);

            Email email = EmailBuilder.startingBlank()
                    .from(cfg.getName(), cfg.getUsernameSmtp())
                    .to(request.getTo())
                    .withSubject(request.getSubject())
                    .withHTMLText(fullBody)
                    .buildEmail();
            mailer.sendMail(email);
           return emailLogRepository.save(emailLogEntity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }


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
                    .withDebugLogging(false)
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
