package mk.ukim.finki.gatewayservice.model;

import java.time.Instant;

public class AlertMessage {
    private String city;
    private String signal;
    private String level;
    private double value;
    private double threshold;
    private Instant timestamp;
    private String reason;

    public AlertMessage() {}

    // getters/setters...
}