package com.example.fileshareR.service;

import com.example.fileshareR.dto.MailBody;
import org.thymeleaf.context.Context;

public interface EmailService {
    void sendSimpleMessage(MailBody mailBody);

    void sendHtmlMessage(String to, String subject, String templateName, Context context);
}
