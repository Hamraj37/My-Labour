package com.mylabour;

import java.io.Serializable;

public class Labour implements Serializable {
    public String id;
    public String name;
    public String email;
    public String number;
    public String address;
    public String uniqueCode;
    public double baseWage;
    public double initialAdvance;

    public Labour() {
        // Default constructor required for calls to DataSnapshot.getValue(Labour.class)
    }

    public Labour(String id, String name, String email, String number, String address) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.number = number;
        this.address = address;
        this.baseWage = 0;
        this.initialAdvance = 0;
        if (id != null) {
            this.uniqueCode = id.length() > 6 ? id.substring(id.length() - 6).toUpperCase() : id.toUpperCase();
        }
    }
}
