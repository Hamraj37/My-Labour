package com.mylabour;

import java.io.Serializable;

public class Payment implements Serializable {
    public String id;
    public double amount;
    public long timestamp;

    public Payment() {
        // Default constructor required for calls to DataSnapshot.getValue(Payment.class)
    }

    public Payment(String id, double amount, long timestamp) {
        this.id = id;
        this.amount = amount;
        this.timestamp = timestamp;
    }
}
