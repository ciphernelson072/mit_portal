package com.sis.service;

import com.sis.dto.PaymentDestinationView;
import com.sis.entity.PaymentChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<PaymentDestinationView> list() {
        boolean bankAvailable = configured(bankAccountName) && configured(bankAccountNumber);
        boolean airtelAvailable = configured(airtelPayee) && configured(airtelMerchantCode);
        boolean mtnAvailable = configured(mtnPayee) && configured(mtnMerchantCode);
        return List.of(
                new PaymentDestinationView(PaymentChannel.BANK_TRANSFER, "Bank transfer", bankAvailable,
                        bankAvailable ? account(bankAccountName, bankAccountNumber, bankBranch) : "Account details not configured",
                        "Use your student number and fee ID as the transfer narration."),
                new PaymentDestinationView(PaymentChannel.PAYPAL, "PayPal", configured(paypalAccount),
                        configured(paypalAccount) ? paypalAccount : "PayPal account not configured",
                        "Pay the exact amount in UGX equivalent and keep the PayPal transaction ID."),
                new PaymentDestinationView(PaymentChannel.MASTERCARD, "Mastercard", false,
                        "Hosted card gateway not configured",
                        "Card payments are unavailable until a hosted gateway with verified server callbacks is integrated. Never enter card details in this portal."),
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
}