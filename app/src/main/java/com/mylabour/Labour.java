package com.mylabour;

public class Labour {
    public String id;
    public String name;
    public String email;
    public String number;
    public String address;

    public Labour() {
        // Default constructor required for calls to DataSnapshot.getValue(Labour.class)
    }

    public Labour(String id, String name, String email, String number, String address) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.number = number;
        this.address = address;
    }
}
