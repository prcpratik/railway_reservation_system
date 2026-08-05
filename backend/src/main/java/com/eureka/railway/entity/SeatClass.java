package com.eureka.railway.entity;

// travel classes a train can offer. Stored as a String in the DB
// (@Enumerated(EnumType.STRING)) so the values stay readable.
public enum SeatClass {

    AC1("1AC"),
    AC2("2AC"),
    AC3("3AC"),
    SLEEPER("Sleeper"),
    GENERAL("General");

    private final String label; // what the user sees

    SeatClass(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
