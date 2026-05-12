package com.wherebus.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API response model for a live vehicle on a route.
 * Returned by GET /api/transit/vehicles and embedded in ETA results.
 *
 * <p>directionId values come from the GTFS-RT trip descriptor:
 * 0 = outbound (away from terminus), 1 = inbound (toward terminus).
 */
@Schema(description = "Live vehicle position on a route.")
public class VehicleResponse {

    @Schema(description = "Vehicle identifier from the GTFS-RT feed.", example = "WB4408N")
    private String vehicleId;

    @Schema(description = "License plate number. Falls back to vehicleId if not broadcast.", example = "WB4408N")
    private String licensePlate;

    @Schema(description = "Current GPS latitude.", example = "3.106551")
    private double latitude;

    @Schema(description = "Current GPS longitude.", example = "101.666084")
    private double longitude;

    @Schema(description = "Direction of travel from the GTFS-RT trip descriptor. 0 = outbound, 1 = inbound.", example = "0")
    private int directionId;

    @Schema(description = "Human-readable direction label.", example = "outbound")
    private String directionLabel;

    public VehicleResponse() {}

    public VehicleResponse(String vehicleId, String licensePlate, double latitude, double longitude,
                           int directionId, String directionLabel) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.directionId = directionId;
        this.directionLabel = directionLabel;
    }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getDirectionId() { return directionId; }
    public void setDirectionId(int directionId) { this.directionId = directionId; }

    public String getDirectionLabel() { return directionLabel; }
    public void setDirectionLabel(String directionLabel) { this.directionLabel = directionLabel; }
}
