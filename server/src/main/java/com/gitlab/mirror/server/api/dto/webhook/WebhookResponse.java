package com.gitlab.mirror.server.api.dto.webhook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Webhook Response
 *
 * @author GitLab Mirror Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {
    private String status;
    private String message;
    
    public static WebhookResponse accepted() {
        return new WebhookResponse("accepted", "Webhook event accepted for processing");
    }
    
    public static WebhookResponse accepted(String message) {
        return new WebhookResponse("accepted", message);
    }
    
    public static WebhookResponse rejected(String message) {
        return new WebhookResponse("rejected", message);
    }
}
