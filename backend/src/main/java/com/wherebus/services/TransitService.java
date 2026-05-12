package com.wherebus.services;

import com.wherebus.models.Route;
import com.wherebus.models.Stop;

import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.opencsv.CSVReader;

/**
 * The core service responsible for holding the unified transit network in memory.
 * Implements the required data structures (Hash Tables, Graphs, Linked Lists)
 * to store and traverse static routing data across multiple GTFS feeds.
 */
@Service
public class TransitService {

    // Thread-safe Hash tables for O(1) fast lookup of stops and routes by their ID across networks
    private final Map<String, Stop> stopDirectory = new HashMap<>();
    private final Map<String, Route> routeDirectory = new HashMap<>();

    // Linked lists to store the strictly ordered sequence of stops for a specific route
    // Key: RouteID (e.g., "T789" or "T815"), Value: Linked List of StopIDs in order
    private final Map<String, LinkedList<String>> routePaths = new HashMap<>();

    // Graph (adjacency list) to map the physical network connections
    // Key: StopID, Value: List of adjacent StopIDs that can be reached directly from the key
    private final Map<String, List<String>> stopGraph = new HashMap<>();

    // Define all target GTFS asset folders to merge
    private static final String[] FEED_DIRECTORIES = {
            "data/rapid-bus-kl",
            "data/rapid-bus-mrtfeeder"
    };

    /**
     * This method runs automatically exactly once when the Spring Boot server starts.
     * It sequentially processes all specified feed directories to unify the static network.
     */
    @PostConstruct
    public void initializeStaticData() {
        System.out.println("Server started. Initializing Unified Multi-Feed Transit Data Structures...");

        for (String folder : FEED_DIRECTORIES) {
            System.out.println("➡️ Parsing static GTFS directory: " + folder);
            try {
                loadStops(folder);
                loadRoutes(folder);
                buildGraphsAndLists(folder);
            } catch (Exception e) {
                System.err.println("❌ Failed to load static data for [" + folder + "]: " + e.getMessage());
            }
        }

        System.out.println("✅ Ingestion Complete. Loaded " + stopDirectory.size() + " unified stops.");
        System.out.println("✅ Loaded " + routeDirectory.size() + " unified routes.");
        System.out.println("✅ Loaded " + routePaths.size() + " total route paths.");
        System.out.println("✅ Loaded " + stopGraph.size() + " vertex connections into the adjacency graph.");
    }

    /**
     * Reads stops.txt from the targeted feed folder and populates the stopDirectory hash table.
     * Injects the parent feed namespace as a structural category property on the entity.
     */
    private void loadStops(String folderPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(folderPath + "/stops.txt");

        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String[] nextLine;
            reader.skip(1);  // Skip header line

            // Extract the operational category tag from the folder path (e.g., "rapid-bus-kl")
            String categoryTag = folderPath.substring(folderPath.lastIndexOf('/') + 1);

            while ((nextLine = reader.readNext()) != null) {
                String id = nextLine[0].trim();
                String name = nextLine[1].trim() + ", " + nextLine[2].trim();
                double lat = Double.parseDouble(nextLine[3]);
                double lon = Double.parseDouble(nextLine[4]);

                // Track and log potential ID namespace collisions across networks safely
                if (stopDirectory.containsKey(id)) {
                    System.out.println("⚠️ [Merge Overwrite] Stop ID " + id + " loaded from multiple networks. Prioritizing definition from: " + categoryTag);
                }

                // Initialize entity and inject network classification label
                Stop stop = new Stop(id, name, lat, lon);
                stop.setCategory(categoryTag);

                stopDirectory.put(id, stop);
            }
        }
    }

    /**
     * Reads routes.txt from the targeted feed folder and populates the routeDirectory hash table.
     */
    private void loadRoutes(String folderPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(folderPath + "/routes.txt");

        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String[] nextLine;
            reader.skip(1);  // Skip header line

            while ((nextLine = reader.readNext()) != null) {
                String id = nextLine[0].trim();
                String name = nextLine[2].trim();
                String longName = nextLine[3].trim();

                if (routeDirectory.containsKey(id)) {
                    System.out.println("⚠️ [Merge Overwrite] Route ID " + id + " collides across feeds. Updating definition.");
                }

                Route route = new Route(id, name, longName);
                routeDirectory.put(id, route);
            }
        }
    }

    /**
     * Reads trips.txt inside the target folder to find representative trip_ids.
     * Captures two trips per route direction to support bidirectional adjacency list mapping.
     */
    private Map<String, String> loadRepresentativeTrips(String folderPath) throws Exception {
        Map<String, String> tripToRouteMap = new HashMap<>();
        Set<String> processedRouteDirections = new HashSet<>();

        ClassPathResource resource = new ClassPathResource(folderPath + "/trips.txt");

        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String[] nextLine;
            reader.skip(1); // Skip header

            while ((nextLine = reader.readNext()) != null) {
                String routeId = nextLine[0].trim();
                String tripId = nextLine[2].trim();
                String directionId = nextLine[4].trim();

                String uniqueRouteDirection = routeId + "_" + directionId;

                if (!processedRouteDirections.contains(uniqueRouteDirection)) {
                    tripToRouteMap.put(tripId, routeId);
                    processedRouteDirections.add(uniqueRouteDirection);

                    routePaths.putIfAbsent(routeId, new LinkedList<>());
                }
            }
        }
        return tripToRouteMap;
    }

    /**
     * Reads stop_times.txt from the targeted feed folder and builds Linked Lists and Graphs.
     */
    private void buildGraphsAndLists(String folderPath) throws Exception {
        Map<String, String> targetTrips = loadRepresentativeTrips(folderPath);
        ClassPathResource resource = new ClassPathResource(folderPath + "/stop_times.txt");

        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String[] nextLine;
            reader.skip(1); // Skip header

            String previousStopId = null;
            String currentTripId = null;

            while ((nextLine = reader.readNext()) != null) {
                String tripId = nextLine[0].trim();
                String stopId = nextLine[3].trim();

                // Fast-fail: Skip rows that do not belong to our target representative trips
                if (!targetTrips.containsKey(tripId)) {
                    continue;
                }

                String routeId = targetTrips.get(tripId);

                // 1. Maintain route Linked List sequence
                LinkedList<String> path = routePaths.get(routeId);
                if (path.isEmpty() || !path.getLast().equals(stopId)) {
                    path.add(stopId);
                }

                // 2. Build Adjacency Graph mapping connections
                if (tripId.equals(currentTripId) && previousStopId != null) {
                    stopGraph.computeIfAbsent(previousStopId, k -> new ArrayList<>()).add(stopId);
                }

                previousStopId = stopId;
                currentTripId = tripId;
            }
        }
    }

    /**
     * Searches unified stop directory matching query parameters.
     */
    public List<Stop> searchStops(String query) {
        List<Stop> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (Stop stop : stopDirectory.values()) {
            if (stop.getName().toLowerCase().contains(lowerQuery)) {
                results.add(stop);
                if (results.size() >= 10) break;
            }
        }
        return results;
    }

    /**
     * Searches unified route directory matching query parameters.
     */
    public List<Route> searchRoutes(String query) {
        List<Route> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        for (Route route : routeDirectory.values()) {
            if (route.getName().toLowerCase().contains(lowerQuery) ||
                    (route.getLongName() != null && route.getLongName().toLowerCase().contains(lowerQuery))) {
                results.add(route);
                if (results.size() >= 10) break;
            }
        }
        return results;
    }

    public Stop getStopById(String id) { return stopDirectory.get(id); }
    public Route getRouteById(String id) { return routeDirectory.get(id); }
    public LinkedList<String> getRoutePath(String routeId) { return routePaths.get(routeId); }
    public List<String> getAdjacentStops(String stopId) { return stopGraph.getOrDefault(stopId, new ArrayList<>()); }
}