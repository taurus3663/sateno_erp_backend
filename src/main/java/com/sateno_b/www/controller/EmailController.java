package com.sateno_b.www.controller;

import com.sateno_b.www.model.dto.EmailDto;
import com.sateno_b.www.model.dto.EmailLogDto;
import com.sateno_b.www.model.entity.EmailEntity;
import com.sateno_b.www.model.entity.EmailLogEntity;
import com.sateno_b.www.model.enums.EmailDirection;
import com.sateno_b.www.model.interfaces.BaseController;
import com.sateno_b.www.model.repository.EmailLogRepository;
import com.sateno_b.www.model.repository.EmailRepository;
import com.sateno_b.www.service.EmailLogService;
import com.sateno_b.www.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController implements BaseController<EmailDto, Long> {

    private final EmailRepository emailRepository;
    private final ModelMapper modelMapper;
    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;
    private final EmailLogService emailLogService;

    @PostMapping("/test-income-connection/{id}")
    public ResponseEntity<Map<String, Object>> testIncomeConnection(@PathVariable Long id) {
        boolean isSuccess = false;
        String message = "";
        try {
            isSuccess = emailService.testIncomingConnection(id);
        } catch (Exception e) {
            message = e.getMessage();
        }

        System.out.println(message);



        return ResponseEntity.ok(Map.of(
                "success", isSuccess,
                "message", message
        ));

    }

    @PostMapping("/test-connection/{id}")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        boolean isSuccess = false;
        String message = "";

        try {
            isSuccess = emailService.testConnection(id);
        } catch (Exception e) {
            message = e.getMessage();
        }

        System.out.println(message);



        return ResponseEntity.ok(Map.of(
                "success", isSuccess,
                "message", message
        ));
    }

    @Override
//    @GetMapping("/list")
    public Page<EmailDto> list(Pageable pageable) {

        return emailRepository.findAll(pageable).map(entity -> {

            EmailDto map = modelMapper.map(entity, EmailDto.class);
            return map;
        });

    }

    @Override
//    @PostMapping("/save")
    public EmailDto save(@RequestBody EmailDto emailDto) {
        EmailEntity map = modelMapper.map(emailDto, EmailEntity.class);
        emailService.updateEmailConfig(map);
     return modelMapper.map(emailRepository.save(map), EmailDto.class);
    }

    @Override
//    @GetMapping("/{id}")
    public EmailDto get(@PathVariable("id") Long id) {
        Optional<EmailEntity> byId = emailRepository.findById(id);
        return modelMapper.map(byId.get(), EmailDto.class);
    }

    @Override
//    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {

        Optional<EmailEntity> byId = emailRepository.findById(id);

        if(byId.isPresent()) {
            try {
                emailService.unregisterEmailListener(byId.get().getUsername());
                emailRepository.deleteById(byId.get().getId());
                return true;
            } catch (Exception e) {
                // Може да гръмне заради Foreign Key констрейнт (напр. имейлът е вързан към поръчка)
                return false;
            }
        }
        return false;
    }


    @GetMapping("/sent/list")
    public Page<EmailLogDto> sentList(Pageable pageable) {

        return emailLogRepository.findAllByDirectionIsOrderByIdDesc(EmailDirection.SENT, pageable)
                .map(entity -> {
                    EmailLogDto map = modelMapper.map(entity, EmailLogDto.class);
                    return map;
                });
    }

    @GetMapping("/receive/list")
    public Page<EmailLogDto> receivedList(Pageable pageable) {
        return emailLogRepository.findAllByDirectionIsOrderByIdDesc(EmailDirection.RECEIVED, pageable)
                .map(entity -> {
                    EmailLogDto map = modelMapper.map(entity, EmailLogDto.class);
                    return map;
                });
    }

    @GetMapping("/seen/{key}")
    public byte[] seen(@PathVariable("key") String key) {
        System.out.println("SEEN " + key);
        key = key.replaceAll("^\"|\"$", "");
        Optional<EmailLogEntity> byPrivateSeenKey = emailLogRepository.findByPrivateSeenKey(key);
        if(byPrivateSeenKey.isPresent()) {
            EmailLogEntity emailLogEntity = byPrivateSeenKey.get();

            if(!emailLogEntity.isSeen()){
                emailLogEntity.setSeen(true);
                emailLogRepository.save(emailLogEntity);
            }

        }
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x08, (byte) 0xD7, 0x63, 0x60, 0x00, 0x02,
                0x00, 0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    @GetMapping(value = "/confirm/{key}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String confirmOrder(@PathVariable String key) {

            boolean success = emailLogService.processConfirmationOrder(key);

        if (success) {
            return """
        <html>
            <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f8f9fa;">
                <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);">
                    <h1 style="color: #28a745;">✅ Благодарим ви!</h1>
                    <p style="font-size: 18px; color: #555;">Вашата поръчка е <strong>потвърдена успешно</strong> и започваме подготовката ѝ.</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                    <p style="font-size: 14px; color: #888;">Ще получите допълнително известие при изпращане.</p>
                </div>
            </body>
        </html>
        """;
        } else {
            return """
        <html>
            <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f8f9fa;">
                <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 4px 10px rgba(0,0,0,0.05);">
                    <h1 style="color: #6c757d;">⚠️ Връзката е неактивна</h1>
                    <p style="font-size: 18px; color: #555;">Този линк е невалиден или поръчката вече е била потвърдена.</p>
                    <p style="font-size: 14px; color: #888;">Ако имате въпроси, моля свържете се с нас.</p>
                </div>
            </body>
        </html>
        """;
        }
    }

    @GetMapping("/cancel/{key}")
    @ResponseBody
    public String cancelOrder(@PathVariable String key) {

        boolean success = emailLogService.processCancelOrder(key);

        if (success) {
            return """
        <html>
            <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f8f9fa;">
                <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 10px; shadow: 0 4px 6px rgba(0,0,0,0.1);">
                    <h1 style="color: #dc3545;">🗑️ Поръчката е отказана</h1>
                    <p style="font-size: 18px; color: #555;">Вашата поръчка беше успешно анулирана в нашата система.</p>
                    <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                </div>
            </body>
        </html>
        """;
        } else {
            return """
        <html>
            <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f8f9fa;">
                <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 10px;">
                    <h1 style="color: #6c757d;">⚠️ Връзката е неактивна</h1>
                    <p style="font-size: 18px; color: #555;">Този линк вече е бил използван или поръчката е в статус, който не позволява отказ.</p>
                </div>
            </body>
        </html>
        """;
        }
    }
}
