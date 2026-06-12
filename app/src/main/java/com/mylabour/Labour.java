package com.mylabour;

import java.io.Serializable;

public class Labour implements Serializable {
    public String id;
    public String name;
    public String email;
    public String number;
    public String address;
    public double dailyAmount;

    public Labour() {
        // Default constructor required for calls to DataSnapshot.getValue(Labour.class)
    }

    public Labour(String id, String name, String email, String number, String address, double dailyAmount) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.number = number;
        this.address = address;
        this.dailyAmount = dailyAmount;
    }
}
