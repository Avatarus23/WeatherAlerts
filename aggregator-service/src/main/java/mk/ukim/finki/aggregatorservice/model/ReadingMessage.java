package mk.ukim.finki.aggregatorservice.model;

import java.time.Instant;

public class ReadingMessage {
    private String city;
    private String signal;
    private double value;
    private String unit;
    private Instant timestamp;

    public ReadingMessage() {}
    // getters/setters...
}