package com.wherebus.models;

import io.swagger.v3.oas.annotations.media.Schema;

/** A physical bus stop. Used as a graph vertex in route path resolution and returned by search and path endpoints. */
@Schema(description = "A physical bus stop or station.")
public class Stop {

    @Schema(description = "Stop ID as defined in stops.txt.", example = "1001410")
    private String id;

    @Schema(description = "Display name of the stop.", example = "KL1441 KL GATEWAY - LRT UNIVERSITI,L/RAYA PERSEKUTUAN")
    private String name;

    @Schema(description = "GPS latitude.", example = "3.1147")
    private double latitude;

    @Schema(description = "GPS longitude.", example = "101.6618")
    private double longitude;

    @Schema(description = "Source feed network. Used by the frontend for stop badge styling.", example = "rapid-bus-kl")
    private String category;

    /** Required by Jackson for JSON serialisation. */
    public Stop() {}

    public Stop(String id, String name, double latitude, double longitude, String category) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
