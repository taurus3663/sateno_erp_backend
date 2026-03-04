package com.sateno_b.www.service;

import com.sateno_b.www.model.dto.EmailSendRequest;
import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.enums.EmailDirection;
import com.sateno_b.www.model.repository.EmailLogRepository;
import com.sateno_b.www.model.repository.EmailRepository;
import com.sateno_b.www.model.repository.WpOrderRepository;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.service.spi.ServiceException;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.dsl.Mail;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class EmailService {


    private final EmailRepository emailRepository;
    private final EmailLogRepository emailLogRepository;
    private final WpOrderRepository wpOrderRepository;
    private final IntegrationFlowContext flowContext;
    @Value("${app.base-url}")
    private String baseUrl;

//    public EmailLogEntity sendEmail(EmailSendRequest request) {
//        EmailEntity cfg = emailRepository.findById(request.getConfigId())
//                .orElseThrow(() -> new RuntimeException("Config not found"));
//
//        String seenKey = UUID.randomUUID().toString();
//        String trackingPixel = "<img src=\"" + baseUrl + "/erp/email/seen/" + seenKey + "\" width=\"1\" height=\"1\" style=\"display:none\">";
//
//        String content = request.getContent();
//        String fullBody = content + trackingPixel;
//
//        TransportStrategy strategy = TransportStrategy.SMTP;
//        if (cfg.isSslSmtp()) {
//            strategy = (cfg.getPortSmtp() == 465) ? TransportStrategy.SMTPS : TransportStrategy.SMTP_TLS;
//        }
//
//        try (Mailer mailer = MailerBuilder
//                .withSMTPServer(cfg.getHostSmtp(), cfg.getPortSmtp(), cfg.getUsernameSmtp(), cfg.getPasswordSmtp())
//                .withTransportStrategy(strategy)
//                .buildMailer()) {
//
//
//            System.out.println("Email sent successfully to " + request.getTo());
//            EmailLogEntity emailLogEntity = new EmailLogEntity();
//            emailLogEntity.setSubject(request.getSubject());
//            emailLogEntity.setConfig(cfg);
//            emailLogEntity.setDirection(EmailDirection.SENT);
////            emailLogEntity.setPrivateConfirmKey(request.getConfirmKey());
//            emailLogEntity.setPrivateSeenKey(seenKey);
//            emailLogEntity.setSender(cfg.getName());
//            emailLogEntity.setRecipient(request.getTo());
//            emailLogEntity.setSeen(false);
//            emailLogEntity.setConfirmed(false);
//
//            if (request.isGenConfirm()) {
//                String confirmKey = UUID.randomUUID().toString();
//
//                // Генерираме двата URL адреса
//                String confirmUrl = baseUrl + "/erp/email/confirm/" + confirmKey;
//                String cancelUrl = baseUrl + "/erp/email/cancel/" + confirmKey;
//
//                String buttonsHtml = """
//        <div style="text-align: center; margin: 25px 0; font-family: Arial, sans-serif;">
//            <a href="%s"
//               style="background-color: green;
//                      color: white;
//                      padding: 12px 25px;
//                      text-decoration: none;
//                      border-radius: 5px;
//                      font-weight: bold;
//                      display: inline-block;
//                      margin: 0 10px 10px 10px;">
//                ПОТВЪРДИ ПОРЪЧКАТА
//            </a>
//
//            <a href="%s"
//               style="background-color: #EF4444;
//                      color: white;
//                      padding: 12px 25px;
//                      text-decoration: none;
//                      border-radius: 5px;
//                      font-weight: bold;
//                      display: inline-block;
//                      margin: 0 10px 10px 10px;">
//                ОТКАЖИ ПОРЪЧКАТА
//            </a>
//        </div>
//        """.formatted(confirmUrl, cancelUrl);
//
//                fullBody += buttonsHtml;
//                emailLogEntity.setPrivateConfirmKey(confirmKey);
//            }
//
//
//            String buttonsHtml = "";
//            String orderTableHtml = "";
//            String customerName = (request.getWpOrderEntity() != null) ? request.getWpOrderEntity().getBilling().getFirstName() : "Клиент";
//            String orderId = (request.getWpOrderEntity() != null) ? request.getWpOrderEntity().getWpOrderId().toString() : "---";
//            String billingAddressHtml = "";
//
//// 2. Генерираме я само ако е поискано (true) и имаме данни за поръчка
//            if (request.isShowItemsTable() && request.getWpOrderEntity() != null) {
//                var order = request.getWpOrderEntity();
//                StringBuilder tableRows = new StringBuilder();
//
//                for (var item : order.getOrderLine()) {
//                    tableRows.append(String.format("""
//            <tr>
//                <td style="text-align:left; border:1px solid #e5e5e5; padding:12px; color: #636363;">%s</td>
//                <td style="text-align:center; border:1px solid #e5e5e5; padding:12px; color: #636363;">%d</td>
//                <td style="text-align:right; border:1px solid #e5e5e5; padding:12px; color: #636363;">%.2f %s</td>
//            </tr>
//            """, item.getProductName(), item.getQuantity(), item.getPrice(), order.getCurrency_symbol()));
//                }
//
//                orderTableHtml = """
//        <h2 style="color: #f4b9b9; font-family: Arial, sans-serif; font-size: 18px; margin: 24px 0 8px;">Детайли на поръчката</h2>
//        <table cellspacing="0" cellpadding="6" style="width:100%%; border:1px solid #e5e5e5; border-collapse:collapse; font-family: Arial, sans-serif;">
//            <thead>
//                <tr>
//                    <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:left;">Продукт</th>
//                    <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:center;">Количество</th>
//                    <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:right;">Цена</th>
//                </tr>
//            </thead>
//            <tbody>%s</tbody>
//            <tfoot>
//                <tr>
//                    <th colspan="2" style="border:1px solid #e5e5e5; padding:12px; text-align:left;">Общо:</th>
//                    <td style="border:1px solid #e5e5e5; padding:12px; text-align:right; font-weight:bold;">%s %s</td>
//                </tr>
//            </tfoot>
//        </table>
//        """.formatted(tableRows.toString(), order.getTotalPrice(), order.getCurrency_symbol());
//
//                // Същото правим и за адреса - показваме го само ако има таблица/поръчка
//                var billing = order.getBilling();
//                billingAddressHtml = String.format("""
//        <div style="margin-top: 30px; font-family: Arial, sans-serif;">
//            <h3 style="color: #f4b9b9; border-bottom: 2px solid #f4b9b9; padding-bottom: 5px;">Адрес за доставка</h3>
//            <p style="color: #636363; line-height: 1.4;">
//                %s %s<br>%s<br>%s, %s<br>%s
//            </p>
//        </div>
//        """, billing.getFirstName(), billing.getLastName(), billing.getAddress1(), billing.getCity(), billing.getPostalCode(), billing.getCountry());
//            }
//
//            // 3. Сглобяване на финалния HTML дизайн ( WooCommerce Style )
//            String finalHtml = """
//        <div style="background-color: #f7f7f7; padding: 20px;">
//            <div style="max-width: 600px; margin: auto; background-color: #ffffff; border: 1px solid #e5e5e5; border-radius: 3px;">
//                <div style="background-color: #f4b9b9; padding: 40px; text-align: center;">
//                    <h1 style="color: #333; font-family: Arial, sans-serif; font-size: 30px; margin: 0;">Благодарим за вашата поръчка</h1>
//                </div>
//                <div style="padding: 40px; font-family: Arial, sans-serif; font-size: 14px; line-height: 150%%; color: #636363;">
//                    <p>Здравейте, %s,</p>
//                    <p>Вашата поръчка #%s е получена и в момента се обработва.</p>
//
//                    %s %s <p style="margin-top: 20px;">Благодарим ви, че избрахте <strong>sateno.bg</strong>!</p>
//                </div>
//            </div>
//            %s </div>
//        """.formatted(customerName, orderId, buttonsHtml, orderTableHtml, trackingPixel);
//
//
//            if (cfg.getSignature() != null) {
//                fullBody += "<br><br>" + cfg.getSignature();
//            }
////            emailLogEntity.setBody(fullBody);
//            emailLogEntity.setBody(finalHtml);
//
//            Email email = EmailBuilder.startingBlank()
//                    .from(cfg.getName(), cfg.getUsernameSmtp())
//                    .to(request.getTo())
//                    .withSubject(request.getSubject())
////                    .withHTMLText(fullBody)
//                    .withHTMLText(finalHtml)
//                    .buildEmail();
//            mailer.sendMail(email);
//           return emailLogRepository.save(emailLogEntity);
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to send email: " + e.getMessage());
//        }
//
//
//    }

public EmailLogEntity sendEmail(EmailSendRequest request) {
    EmailEntity cfg = emailRepository.findById(request.getConfigId())
            .orElseThrow(() -> new RuntimeException("Config not found"));

    String seenKey = UUID.randomUUID().toString();
    String trackingPixel = "<img src=\"" + baseUrl + "/erp/email/seen/" + seenKey + "\" width=\"1\" height=\"1\" style=\"display:none\">";

    // Инициализираме променливите в началото
    String buttonsHtml = "";
    String orderTableHtml = "";
    String billingAddressHtml = "";
    String confirmKey = null;



    String customerName = (request.getWpOrderEntity() != null) ? request.getWpOrderEntity().getBilling().getFirstName() : "Клиент";
    String orderId = (request.getWpOrderEntity() != null) ? request.getWpOrderEntity().getWpOrderId().toString() : "---";

    // 1. Генериране на бутони за потвърждение
    if (request.isGenConfirm()) {
        confirmKey = UUID.randomUUID().toString();
        String confirmUrl = baseUrl + "/erp/email/confirm/" + confirmKey;
        String cancelUrl = baseUrl + "/erp/email/cancel/" + confirmKey;

        buttonsHtml = """
            <div style="text-align: center; margin: 25px 0; font-family: Arial, sans-serif;">
                <a href="%s" style="background-color: green; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block; margin: 5px;">ПОТВЪРДИ ПОРЪЧКАТА</a>
                <a href="%s" style="background-color: #EF4444; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block; margin: 5px;">ОТКАЖИ ПОРЪЧКАТА</a>
            </div>
            """.formatted(confirmUrl, cancelUrl);
    }

    // 2. Генериране на таблица с продукти и адрес
    if (request.isShowItemsTable() && request.getWpOrderEntity() != null) {
        var order = request.getWpOrderEntity();
        StringBuilder tableRows = new StringBuilder();

        for (var item : order.getOrderLine()) {
            tableRows.append(String.format("""
                <tr>
                    <td style="text-align:left; border:1px solid #e5e5e5; padding:12px; color: #636363;">%s</td>
                    <td style="text-align:center; border:1px solid #e5e5e5; padding:12px; color: #636363;">%d</td>
                    <td style="text-align:right; border:1px solid #e5e5e5; padding:12px; color: #636363;">%.2f %s</td>
                </tr>
                """, item.getProductName(), item.getQuantity(), item.getPrice(), order.getCurrency_symbol()));
        }

        orderTableHtml = """
            <h2 style="color: #f4b9b9; font-family: Arial, sans-serif; font-size: 18px; margin: 24px 0 8px;">Детайли на поръчката</h2>
            <table cellspacing="0" cellpadding="6" style="width:100%%; border:1px solid #e5e5e5; border-collapse:collapse; font-family: Arial, sans-serif;">
                <thead>
                    <tr>
                        <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:left;">Продукт</th>
                        <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:center;">Количество</th>
                        <th style="background-color:#f8f8f8; border:1px solid #e5e5e5; padding:12px; text-align:right;">Цена</th>
                    </tr>
                </thead>
                <tbody>%s</tbody>
                <tfoot>
                    <tr>
                        <th colspan="2" style="border:1px solid #e5e5e5; padding:12px; text-align:left;">Общо:</th>
                        <td style="border:1px solid #e5e5e5; padding:12px; text-align:right; font-weight:bold;">%.2f %s</td>
                    </tr>
                </tfoot>
            </table>
            """.formatted(tableRows.toString(), order.getTotalPrice(), order.getCurrency_symbol());

        var billing = order.getBilling();
        billingAddressHtml = String.format("""
            <div style="margin-top: 30px; font-family: Arial, sans-serif;">
                <h3 style="color: #f4b9b9; border-bottom: 2px solid #f4b9b9; padding-bottom: 5px;">Адрес за доставка</h3>
                <p style="color: #636363; line-height: 1.4;">
                    %s %s<br>%s<br>%s, %s<br>%s
                </p>
            </div>
            """, billing.getFirstName(), billing.getLastName(), billing.getAddress1(), billing.getCity(), billing.getPostalCode(), billing.getCountry());
    }

    // 3. Сглобяване на финалния HTML
    String signature = (cfg.getSignature() != null) ? "<br><br>" + cfg.getSignature() : "";

    String finalHtml = """
        <div style="background-color: #f7f7f7; padding: 20px;">
            <div style="max-width: 600px; margin: auto; background-color: #ffffff; border: 1px solid #e5e5e5; border-radius: 3px;">
                <div style="background-color: #f4b9b9; padding: 40px; text-align: center;">
                    <h1 style="color: #333; font-family: Arial, sans-serif; font-size: 30px; margin: 0;">Благодарим за вашата поръчка</h1>
                </div>
                <div style="padding: 40px; font-family: Arial, sans-serif; font-size: 14px; line-height: 150%%; color: #636363;">
                    <p>Здравейте, %s,</p>
                    <p>Вашата поръчка #%s е получена.</p>
                    
                    <div style="text-align: center; margin: 20px 0; font-size: 16px; color: #333;">
                                        %s
                                    </div>
                    
                    %s %s %s <p style="margin-top: 20px;"></p>
                    %s </div>
            </div>
            %s </div>
        """.formatted(customerName, orderId, request.getContent(), buttonsHtml, orderTableHtml, billingAddressHtml, signature, trackingPixel);

    // 4. Изпращане
    TransportStrategy strategy = cfg.isSslSmtp() ?
            ((cfg.getPortSmtp() == 465) ? TransportStrategy.SMTPS : TransportStrategy.SMTP_TLS) : TransportStrategy.SMTP;

    try (Mailer mailer = MailerBuilder
            .withSMTPServer(cfg.getHostSmtp(), cfg.getPortSmtp(), cfg.getUsernameSmtp(), cfg.getPasswordSmtp())
            .withTransportStrategy(strategy)
            .buildMailer()) {

        Email email = EmailBuilder.startingBlank()
                .from(cfg.getName(), cfg.getUsernameSmtp())
                .to(request.getTo())
                .withSubject(request.getSubject())
                .withHTMLText(finalHtml)
                .buildEmail();

        mailer.sendMail(email);

        // 5. Запис в лога
        EmailLogEntity emailLogEntity = new EmailLogEntity();
        emailLogEntity.setSubject(request.getSubject());
        emailLogEntity.setConfig(cfg);
        emailLogEntity.setDirection(EmailDirection.SENT);
        emailLogEntity.setPrivateSeenKey(seenKey);
        emailLogEntity.setPrivateConfirmKey(confirmKey);
        emailLogEntity.setSender(cfg.getName());
        emailLogEntity.setRecipient(request.getTo());
        emailLogEntity.setSeen(false);
        emailLogEntity.setConfirmed(false);
        emailLogEntity.setBody(finalHtml);

        return emailLogRepository.save(emailLogEntity);
    } catch (Exception e) {
        throw new RuntimeException("Failed to send email: " + e.getMessage());
    }
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

    @EventListener(ApplicationReadyEvent.class)
    private void startAllEmailListeners() {
        System.out.println("STARTED");
        List<EmailEntity> emails = emailRepository.findAllByActiveTrue();
        emails.forEach(this::registerEmailListener);
    }

    private void registerEmailListener(EmailEntity cfg) {
        try {
            // 1. Енкодване на потребителското име и паролата (Важно за символи като @ и #)
            String encodedUser = URLEncoder.encode(cfg.getUsername(), StandardCharsets.UTF_8);
            String encodedPass = URLEncoder.encode(cfg.getPassword(), StandardCharsets.UTF_8);

            // 2. Конструиране на пълния URL
            String url = String.format("imaps://%s:%s@%s:%d/INBOX",
                    encodedUser, encodedPass, cfg.getHost(), cfg.getPort());

            ImapMailReceiver receiver = new ImapMailReceiver(url);
            receiver.setSimpleContent(true);
            receiver.setShouldMarkMessagesAsRead(false);

            // 3. Добавяне на допълнителни свойства за сигурност
            Properties props = new Properties();
            props.put("mail.debug", "false");
            props.put("mail.imaps.auth", "true");
            props.put("mail.imaps.ssl.enable", "true");
            receiver.setJavaMailProperties(props);

            // 4. Създаване на Integration Flow
            IntegrationFlow flow = IntegrationFlow
                    .from(Mail.imapIdleAdapter(receiver))
                    .handle(message -> {
                        if (message.getPayload() instanceof MimeMessage mimeMessage) {
                            processIncomingEmail(mimeMessage, cfg);
                        }
                    })
                    .get();

            // 5. Регистриране
            flowContext.registration(flow)
                    .id(cfg.getUsername() + "_flow")
                    .register();

            System.out.println("✅ Слушателят е активиран за: " + cfg.getUsername());

        } catch (Exception e) {
            System.err.println("❌ Грешка при регистрация на " + cfg.getUsername() + ": " + e.getMessage());
        }
    }

    private void processIncomingEmail(MimeMessage msg, EmailEntity cfg) {
        try {
            System.out.println(msg.toString());
            // 1. Извличане на данни от писмото
            String subject = msg.getSubject();
            String senderEmail = extractEmailAddress(msg.getFrom()[0].toString());
            String body = getTextFromMessage(msg);

            // 2. Създаване на лог запис
            EmailLogEntity log = new EmailLogEntity();
            log.setSubject(subject);
            log.setBody(body);
            log.setSender(senderEmail);
            log.setRecipient(cfg.getUsername()); // твоята поща
            log.setConfig(cfg);
            log.setDirection(EmailDirection.RECEIVED);
//            log.setSentAt(Instant.now());

            // 3. СВЪРЗВАНЕ С ПОРЪЧКА (Много важно!)
            // Търсим последната поръчка на този клиент по имейл
//            wpOrderRepository.findFirstByCustomerEmailOrderByWpOrderTimeDesc(senderEmail)
//                    .ifPresent(log::setOrder);

            // 4. Запис в DB
            emailLogRepository.save(log);

            // 5. Нотификация към Angular
            // Пращаме целия обект, за да може Angular да го добави в списъка веднага
//            messagingTemplate.convertAndSend("/topic/emails", log);

        } catch (Exception e) {
            log.error("Грешка при обработка на входящ имейл", e);
        }
    }

    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return message.getContent().toString();
        } else if (message.isMimeType("text/html")) {
            return message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            // Взимаме първата част (обикновено текста)
            return mimeMultipart.getBodyPart(0).getContent().toString();
        }
        return "";
    }

    private String extractEmailAddress(String fromHeader) {
        try {
            // InternetAddress автоматично разделя "Name <email@domain.com>"
            InternetAddress address = new InternetAddress(fromHeader);
            return address.getAddress().toLowerCase().trim();
        } catch (Exception e) {
            // Ако форматът е счупен, опитайте с прост RegEx като резервен вариант
            return fromHeader.replaceAll(".*<|>", "").toLowerCase().trim();
        }
    }

    public void unregisterEmailListener(String username) {
        String flowId = username + "_flow";
        try {
            // Проверяваме дали съществува такъв поток
            IntegrationFlowContext.IntegrationFlowRegistration registration = flowContext.getRegistrationById(flowId);
            if (registration != null) {
                registration.destroy(); // Спира адаптера и затваря връзката към IMAP
                log.info("🛑 Слушателят за " + username + " е спрян.");
            }
        } catch (Exception e) {
            System.err.println("Грешка при спиране на поток: " + e.getMessage());
        }
    }

    public void updateEmailConfig(EmailEntity cfg) {
        // 1. Първо спираме стария слушател (за всеки случай)
        unregisterEmailListener(cfg.getUsername());

        // 3. Ако е активен, го пускаме наново с новите настройки
        if (cfg.isActive()) {
            registerEmailListener(cfg);
        }
    }


}
