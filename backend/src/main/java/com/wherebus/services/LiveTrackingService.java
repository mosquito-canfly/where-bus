package com.wherebus.services;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for fetching, parsing, and storing live Prasarana bus GPS telemetry.
 * Concurrently polls multiple operational GTFS-Realtime streams to feed a centralized fleet engine.
 */
@Service
public class LiveTrackingService {

    // Shared thread-safe Hash Table hosting actively moving buses across ALL integrated divisions.
    // Key: Vehicle ID, Value: The latest parsed VehiclePosition Protobuf object
    private final Map<String, VehiclePosition> activeVehicles = new ConcurrentHashMap<>();

    // Master API ingestion endpoints mapping standard RapidKL and MRT Feeder bus networks
    private static final String[] LIVE_FEED_URLS = {
            "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana?category=rapid-bus-kl",
            "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana?category=rapid-bus-mrtfeeder"
    };

    /**
     * The Multi-Stream Heartbeat Worker.
     * Runs automatically every 30 seconds to fetch and consolidate parallel binary Protobuf streams.
     */
    @Scheduled(fixedRate = 30000)
    public void refreshLiveVehiclePositions() {
        System.out.println("⏳ Polling multi-feed GTFS-RT agency vehicle streams...");
        int totalUpdatedCount = 0;

        for (String targetFeedUrl : LIVE_FEED_URLS) {
            try {
                URL url = new URI(targetFeedUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Configure production headers and protective timeout guards
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "WhereBus-UnifiedEngine/2.0");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {

                    // Decode incoming binary packet streams directly into structured Protobuf instances
                    try (InputStream inputStream = connection.getInputStream()) {
                        FeedMessage feed = FeedMessage.parseFrom(inputStream);

                        for (FeedEntity entity : feed.getEntityList()) {
                            if (entity.hasVehicle()) {
                                VehiclePosition vehicle = entity.getVehicle();
                                String vehicleId = vehicle.getVehicle().getId();

                                // Merge active updates directly into the central, universal fleet memory space
                                activeVehicles.put(vehicleId, vehicle);
                                totalUpdatedCount++;
                            }
                        }
                    }
                } else {
                    System.err.println("⚠️ Unexpected response code [" + responseCode + "] received from endpoint: " + targetFeedUrl);
                }

                connection.disconnect();

            } catch (Exception e) {
                System.err.println("❌ Failed to fetch live stream [" + targetFeedUrl + "]: " + e.getMessage());
            }
        }

        System.out.println("✅ Consolidated Tracking Engine active: Ingested " + totalUpdatedCount + " live vehicles across all integrated divisions.");
    }

    /**
     * O(1) concurrent access to the fully merged live fleet.
     */
    public Map<String, VehiclePosition> getActiveVehicles() {
        return activeVehicles;
    }

    /**
     * O(1) direct telemetry lookup for any vehicle across operational agencies.
     */
    public VehiclePosition getVehicleById(String vehicleId) {
        return activeVehicles.get(vehicleId);
    }
}