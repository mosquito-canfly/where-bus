package com.wherebus.controllers;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.wherebus.models.Route;
import com.wherebus.models.Stop;
import com.wherebus.services.EtaCalculationService;
import com.wherebus.services.LiveTrackingService;
import com.wherebus.services.TransitService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller serving static transit structures, real-time arrival predictions,
 * and live fleet debugging telemetry.
 */
@RestController
@RequestMapping("/api/transit")
@CrossOrigin(origins = "http://localhost:3000") // Allows Next.js to fetch data without CORS blocks
public class TransitController {

    private final TransitService transitService;
    private final EtaCalculationService etaCalculationService;
    private final LiveTrackingService liveTrackingService;

    // Spring automatically injects all three required services here
    public TransitController(
            TransitService transitService,
            EtaCalculationService etaCalculationService,
            LiveTrackingService liveTrackingService) {
        this.transitService = transitService;
        this.etaCalculationService = etaCalculationService;
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * 1. THE QUICK TEST ENDPOINT
     * Hit http://localhost:8080/api/transit/test to verify the service is injected properly.
     */
    @GetMapping("/test")
    public String testDataLink() {
        Stop stop = transitService.getStopById("1006035");
        if (stop != null) {
            return "TransitService is wired up! Found stop: " + stop.getName();
        }
        return "TransitService is wired up, but stop 1006035 is missing.";
    }

    /**
     * Retrieves the basic details of a specific route.
     * Example: GET /api/transit/routes/T789
     */
    @GetMapping("/routes/{routeId}")
    public Route getRouteDetails(@PathVariable String routeId) {
        return transitService.getRouteById(routeId);
    }

    /**
     * Retrieves the exact sequence of stops for a given route.
     * This converts the linked list of string IDs into a list of actual Stop objects
     * (containing latitudes and longitudes) so the Next.js map can draw the line.
     * Example: GET /api/transit/routes/T789/path
     */
    @GetMapping("/routes/{routeId}/path")
    public List<Stop> getRoutePath(@PathVariable String routeId) {
        LinkedList<String> stopIds = transitService.getRoutePath(routeId);
        List<Stop> path = new ArrayList<>();

        if (stopIds == null) {
            return path; // Return empty list if route doesn't exist
        }

        // Loop through the linked list and fetch the actual coordinate data from the hash table
        for (String stopId : stopIds) {
            Stop stop = transitService.getStopById(stopId);
            if (stop != null) {
                path.add(stop);
            }
        }

        return path;
    }

    /**
     * Unified search endpoint for stops and routes.
     * Example: GET /api/transit/search?q=Masjid
     */
    @GetMapping("/search")
    public Map<String, Object> searchTransit(@RequestParam String q) {
        Map<String, Object> response = new HashMap<>();

        if (q == null || q.trim().isEmpty()) {
            response.put("stops", new ArrayList<>());
            response.put("routes", new ArrayList<>());
            return response;
        }

        response.put("stops", transitService.searchStops(q));
        response.put("routes", transitService.searchRoutes(q));

        return response;
    }

    /**
     * Retrieves sorted real-time ETAs for a specific stop.
     * Example: GET /api/transit/eta?routeId=T789&stopId=100432
     */
    @GetMapping("/eta")
    public List<Map<String, Object>> getRealtimeEta(
            @RequestParam String routeId,
            @RequestParam String stopId) {
        return etaCalculationService.getArrivalsForStop(routeId, stopId);
    }

    /**
     * Retrieves live GPS coordinates for all buses actively running on a route.
     * Example: GET /api/transit/vehicles?routeId=T789
     */
    @GetMapping("/vehicles")
    public List<Map<String, Object>> getLiveVehiclesForRoute(@RequestParam String routeId) {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        Map<String, com.google.transit.realtime.GtfsRealtime.VehiclePosition> fleet =
                liveTrackingService.getActiveVehicles();

        for (Map.Entry<String, com.google.transit.realtime.GtfsRealtime.VehiclePosition> entry : fleet.entrySet()) {
            com.google.transit.realtime.GtfsRealtime.VehiclePosition v = entry.getValue();

            if (v.hasTrip()) {
                String broadcastedRouteId = v.getTrip().getRouteId();

                // Match requested route exactly OR match with Prasarana's appended '0'
                if (routeId.equalsIgnoreCase(broadcastedRouteId) || (routeId + "0").equalsIgnoreCase(broadcastedRouteId)) {
                    Map<String, Object> busData = new HashMap<>();
                    busData.put("vehicleId", entry.getKey());
                    busData.put("latitude", v.getPosition().getLatitude());
                    busData.put("longitude", v.getPosition().getLongitude());
                    busData.put("licensePlate", v.getVehicle().hasLicensePlate()
                            ? v.getVehicle().getLicensePlate()
                            : entry.getKey());

                    vehicles.add(busData);
                }
            }
        }
        return vehicles;
    }

    /**
     * TEMPORARY DEBUG ENDPOINT
     * Hit http://localhost:8080/api/transit/debug-fleet to see exactly how Prasarana
     * is formatting their active Route IDs right now.
     */
    @GetMapping("/debug-fleet")
    public Map<String, Object> debugActiveFleet() {
        Map<String, Object> response = new HashMap<>();
        Map<String, VehiclePosition> fleet = liveTrackingService.getActiveVehicles();

        response.put("totalActiveBuses", fleet.size());

        // Collect all unique Route IDs currently moving in the city
        Set<String> activeRouteIds = new HashSet<>();
        List<Map<String, Object>> sampleBuses = new ArrayList<>();

        int count = 0;
        for (Map.Entry<String, VehiclePosition> entry : fleet.entrySet()) {
            VehiclePosition v = entry.getValue();

            String routeId = v.hasTrip() ? v.getTrip().getRouteId() : "NO_ROUTE_ID";
            activeRouteIds.add(routeId);

            // Grab a few samples to inspect the raw data structure
            if (count < 5) {
                Map<String, Object> sample = new HashMap<>();
                sample.put("vehicleId", entry.getKey());
                sample.put("broadcastedRouteId", routeId);
                sample.put("lat", v.getPosition().getLatitude());
                sample.put("lon", v.getPosition().getLongitude());
                sampleBuses.add(sample);
                count++;
            }
        }

        response.put("uniqueRoutesActiveNow", activeRouteIds);
        response.put("sampleVehicleData", sampleBuses);

        return response;
    }
}