package com.wherebus.services;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.wherebus.models.Stop;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for calculating real-time ETAs using geographic math
 * and sorting incoming arrivals via a Priority Queue (Min-Heap).
 */
@Service
public class EtaCalculationService {

    private final TransitService transitService;
    private final LiveTrackingService liveTrackingService;

    // Average inner-city bus speed in Kuala Lumpur (roughly 20 km/h expressed in meters per second)
    private static final double DEFAULT_BUS_SPEED_MPS = 5.5;

    public EtaCalculationService(TransitService transitService, LiveTrackingService liveTrackingService) {
        this.transitService = transitService;
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * Calculates and orders all approaching buses for a specific stop on a specific route.
     */
    public List<Map<String, Object>> getArrivalsForStop(String routeId, String targetStopId) {
        Stop targetStop = transitService.getStopById(targetStopId);
        if (targetStop == null) {
            return Collections.emptyList();
        }

        // 1. Initialize a Priority Queue that sorts arrivals by the lowest ETA (seconds remaining)
        PriorityQueue<ArrivalPrediction> arrivalHeap = new PriorityQueue<>(
                Comparator.comparingInt(ArrivalPrediction::getSecondsRemaining)
        );

        // 2. Grab the live fleet from memory
        Map<String, VehiclePosition> activeFleet = liveTrackingService.getActiveVehicles();


        // 3. Filter and calculate ETAs
        for (Map.Entry<String, VehiclePosition> entry : activeFleet.entrySet()) {
            VehiclePosition vehicle = entry.getValue();

            if (vehicle.hasTrip()) {
                String broadcastedRouteId = vehicle.getTrip().getRouteId();

                // Match requested "T789" exactly OR match broadcasted "T7890"
                boolean matchesRoute = routeId.equalsIgnoreCase(broadcastedRouteId) ||
                        (routeId + "0").equalsIgnoreCase(broadcastedRouteId);

                if (matchesRoute) {
                    double busLat = vehicle.getPosition().getLatitude();
                    double busLon = vehicle.getPosition().getLongitude();

                    double distanceMeters = calculateHaversineDistance(
                            busLat, busLon, targetStop.getLatitude(), targetStop.getLongitude()
                    );

                    double speedMps = vehicle.getPosition().hasSpeed() && vehicle.getPosition().getSpeed() > 1.0
                            ? vehicle.getPosition().getSpeed()
                            : DEFAULT_BUS_SPEED_MPS;

                    int secondsRemaining = (int) (distanceMeters / speedMps);

                    // Safely extract the direction flag from the moving bus
                    int directionId = vehicle.getTrip().hasDirectionId() ? vehicle.getTrip().getDirectionId() : 0;

                    String licensePlate = vehicle.getVehicle().hasLicensePlate()
                            ? vehicle.getVehicle().getLicensePlate()
                            : entry.getKey();

                    // Push the direction flag into the Min-Heap alongside the telemetry
                    arrivalHeap.offer(new ArrivalPrediction(
                            entry.getKey(), licensePlate, distanceMeters, secondsRemaining, directionId
                    ));
                }
            }
        }

        // 4. Drain the Priority Queue into an ordered list for the JSON response
        List<Map<String, Object>> sortedArrivals = new ArrayList<>();
        while (!arrivalHeap.isEmpty()) {
            ArrivalPrediction prediction = arrivalHeap.poll();

            Map<String, Object> payload = new HashMap<>();
            payload.put("vehicleId", prediction.getVehicleId());
            payload.put("licensePlate", prediction.getLicensePlate());
            payload.put("distanceMeters", Math.round(prediction.getDistanceMeters()));
            payload.put("etaSeconds", prediction.getSecondsRemaining());
            payload.put("etaFormatted", formatEta(prediction.getSecondsRemaining()));

            // Expose the direction outputs to the frontend JSON structure
            payload.put("directionId", prediction.getDirectionId());
            payload.put("directionLabel", prediction.getDirectionId() == 0 ? "outbound" : "inbound");

            sortedArrivals.add(payload);
        }

        return sortedArrivals;
    }

    /**
     * The Haversine Formula.
     * Calculates the great-circle distance between two GPS coordinates in meters.
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String formatEta(int totalSeconds) {
        if (totalSeconds < 60) return "Arriving";
        int minutes = totalSeconds / 60;
        return minutes + " min";
    }

    /**
     * Internal helper class representing an item inside our Priority Queue.
     */
    private static class ArrivalPrediction {
        private final String vehicleId;
        private final String licensePlate;
        private final double distanceMeters;
        private final int secondsRemaining;
        private final int directionId; // Added direction tracking

        public ArrivalPrediction(String vehicleId, String licensePlate, double distanceMeters, int secondsRemaining, int directionId) {
            this.vehicleId = vehicleId;
            this.licensePlate = licensePlate;
            this.distanceMeters = distanceMeters;
            this.secondsRemaining = secondsRemaining;
            this.directionId = directionId;
        }

        public String getVehicleId() { return vehicleId; }
        public String getLicensePlate() { return licensePlate; }
        public double getDistanceMeters() { return distanceMeters; }
        public int getSecondsRemaining() { return secondsRemaining; }
        public int getDirectionId() { return directionId; }
    }
}