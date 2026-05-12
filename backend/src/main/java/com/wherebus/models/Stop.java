package com.wherebus.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a physical bus stop within the transit network.
 * This object is used as a vertex (node) within the routing Graph and
 * is stored in a Hash Table for fast O(1) lookups.
 */
@Schema(description = "Entity representing a physical transit stop or station along a route.")
public class Stop {

    @Schema(description = "Unique system identifier for the stop.", example = "1001410")
    private String id;

    @Schema(description = "Public display name of the transit stop.", example = "KL1441 KL GATEWAY - LRT UNIVERSITI,L/RAYA PERSEKUTUAN")
    private String name;

    @Schema(description = "Geographic latitude coordinate.", example = "3.1147")
    private double latitude;

    @Schema(description = "Geographic longitude coordinate.", example = "101.6618")
    private double longitude;

    @Schema(description = "Operational network classification tag driving client-side badges.", example = "rapid-bus-kl")
    private String category;

    /**
     * Default no-argument constructor required by Spring Data / Serialization libraries.
     */
    public Stop() {
    }

    /**
     * Constructs a new Stop instance without category initialization.
     *
     * @param id        The unique identifier for the bus stop (e.g., "1001410").
     * @param name      The human-readable name of the stop.
     * @param latitude  The geographical latitude coordinate of the stop.
     * @param longitude The geographical longitude coordinate of the stop.
     */
    public Stop(String id, String name, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Constructs a new Stop instance with full metadata including network categorization.
     *
     * @param id        The unique identifier for the bus stop.
     * @param name      The human-readable name of the stop.
     * @param latitude  The geographical latitude coordinate.
     * @param longitude The geographical longitude coordinate.
     * @param category  The parent directory network string (e.g., "rapid-bus-kl" or "rapid-bus-mrtfeeder").
     */
    public Stop(String id, String name, double latitude, double longitude, String category) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
    }

    /** @return The unique identifier of the stop. */
    public String getId() { return id; }

    /** @param id The unique identifier to set. */
    public void setId(String id) { this.id = id; }

    /** @return The human-readable name of the stop. */
    public String getName() { return name; }

    /** @param name The human-readable name to set. */
    public void setName(String name) { this.name = name; }

    /** @return The geographical latitude coordinate. */
    public double getLatitude() { return latitude; }

    /** @param latitude The geographical latitude to set. */
    public void setLatitude(double latitude) { this.latitude = latitude; }

    /** @return The geographical longitude coordinate. */
    public double getLongitude() { return longitude; }

    /** @param longitude The geographical longitude to set. */
    public void setLongitude(double longitude) { this.longitude = longitude; }

    /** @return The network category tag of the stop. */
    public String getCategory() { return category; }

    /** @param category The network category string to set. */
    public void setCategory(String category) { this.category = category; }
}