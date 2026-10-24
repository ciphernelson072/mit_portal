package com.sis.service;

import com.sis.dto.PaymentDestinationView;
import com.sis.entity.PaymentChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.net.URI;

@Service
public class PaymentDestinationService {
    @Value("${app.finance.destinations.bank.account-name:}")
    private String bankAccountName;
    @Value("${app.finance.destinations.bank.account-number:}")
    private String bankAccountNumber;
    @Value("${app.finance.destinations.bank.branch:}")
    private String bankBranch;
    @Value("${app.finance.destinations.paypal.account:}")
    private String paypalAccount;
    @Value("${app.finance.destinations.airtel.payee:}")
    private String airtelPayee;
    @Value("${app.finance.destinations.airtel.merchant-code:}")
    private String airtelMerchantCode;
    @Value("${app.finance.destinations.mtn.payee:}")
    private String mtnPayee;
    @Value("${app.finance.destinations.mtn.merchant-code:}")
    private String mtnMerchantCode;
    @Value("${app.finance.flutterwave.secret-key:}")
    private String flutterwaveSecretKey;
    @Value("${app.finance.flutterwave.webhook-secret-hash:}")
    private String flutterwaveWebhookSecretHash;
    @Value("${app.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    public List<PaymentDestinationView> list() {
        boolean bankAvailable = configured(bankAccountName) && configured(bankAccountNumber);
        boolean airtelAvailable = configured(airtelPayee) && configured(airtelMerchantCode);
        boolean mtnAvailable = configured(mtnPayee) && configured(mtnMerchantCode);
        boolean flutterwaveAvailable = configured(flutterwaveSecretKey)
                && configured(flutterwaveWebhookSecretHash) && validFrontendUrl();
        return List.of(
                new PaymentDestinationView(PaymentChannel.BANK_TRANSFER, "Bank transfer", bankAvailable,
                        bankAvailable ? account(bankAccountName, bankAccountNumber, bankBranch) : "Account details not configured",
                        "Use your student number and fee ID as the transfer narration."),
                new PaymentDestinationView(PaymentChannel.PAYPAL, "PayPal", configured(paypalAccount),
                        configured(paypalAccount) ? paypalAccount : "PayPal account not configured",
                        "Pay the exact amount in UGX equivalent and keep the PayPal transaction ID."),
                new PaymentDestinationView(PaymentChannel.MASTERCARD, "Mastercard", false,
                        "Use the Flutterwave secure checkout",
                        "This legacy direct-card channel is not enabled. Use Flutterwave's hosted checkout for available card payments. Never enter card details in this portal."),
                new PaymentDestinationView(PaymentChannel.FLUTTERWAVE, "Flutterwave secure checkout", flutterwaveAvailable,
                        flutterwaveAvailable ? "Hosted checkout | Card and Uganda mobile money"
                                : "Flutterwave is not configured",
                        "Continue to Flutterwave's secure hosted page. This portal never collects or stores card details, PINs, or one-time codes."),
                new PaymentDestinationView(PaymentChannel.AIRTEL_MONEY, "Airtel Money", airtelAvailable,
                        airtelAvailable ? account(airtelPayee, airtelMerchantCode, null) : "Merchant details not configured",
                        "Pay to the registered merchant code and keep the mobile-money transaction ID."),
                new PaymentDestinationView(PaymentChannel.MTN_MOMO, "MTN MoMo", mtnAvailable,
                        mtnAvailable ? account(mtnPayee, mtnMerchantCode, null) : "Merchant details not configured",
                        "Pay to the registered merchant code and keep the mobile-money transaction ID.")
        );
    }

    private String account(String name, String number, String branch) {
        String value = name + " | " + number;
        return configured(branch) ? value + " | " + branch : value;
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private boolean validFrontendUrl() {
        try {
            URI uri = URI.create(frontendUrl.trim());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}