package com.example.fileshareR.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payment")
@Getter
@Setter
public class PaymentProperties {

    private Vnpay vnpay = new Vnpay();
    private Momo momo = new Momo();
    private String frontendReturnUrl;

    @Getter
    @Setter
    public static class Vnpay {
        private String tmnCode;
        private String hashSecret;
        private String payUrl;
        private String returnUrl;
        private String version = "2.1.0";
        private String command = "pay";
        private String currCode = "VND";
        private String locale = "vn";
        private int expireMinutes = 15;

        public boolean isConfigured() {
            return tmnCode != null && !tmnCode.isBlank()
                    && hashSecret != null && !hashSecret.isBlank()
                    && payUrl != null && !payUrl.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Momo {
        private String partnerCode;
        private String accessKey;
        private String secretKey;
        private String endpoint;
        private String returnUrl;
        private String ipnUrl;
        private String requestType = "captureWallet";
        private String lang = "vi";

        public boolean isConfigured() {
            return partnerCode != null && !partnerCode.isBlank()
                    && accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank()
                    && endpoint != null && !endpoint.isBlank();
        }
    }
}
