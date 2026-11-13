package com.sis.dto;

import com.sis.entity.PaymentChannel;

public class PaymentDestinationView {
    private final PaymentChannel channel;
    private final String label;
    private final boolean available;
    private final String destination;
    private final String instructions;

    public PaymentDestinationView(PaymentChannel channel, String label, boolean available,
                                  String destination, String instructions) {
        this.channel = channel;
        this.label = label;
        this.available = available;
        this.destination = destination;
        this.instructions = instructions;
    }

    public PaymentChannel getChannel() { return channel; }
    public String getLabel() { return label; }
    public boolean isAvailable() { return available; }
    public String getDestination() { return destination; }
    public String getInstructions() { return instructions; }
}