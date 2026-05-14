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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls Prasarana's GTFS-Realtime feeds every 30 seconds and maintains a merged,
 * thread-safe snapshot of all active vehicle positions.
 *
 * <p><b>Polling strategy:</b> The two feeds are fetched sequentially with
 * {@code INTER_FEED_DELAY_MS} (10 seconds) between them to reduce back-to-back
 * rate limit hits on data.gov.my. Each feed tracks its own consecutive failure count;
 * on a 429 the feed is skipped for {@code BACKOFF_CYCLES} (4) cycles = 2 minutes.
 *
 * <p><b>Blackout-safe eviction:</b> Stale vehicle eviction only runs when at least one
 * feed was attempted in the current cycle. During a full rate-limit blackout (both feeds
 * in backoff), the last known positions are preserved so ETA and vehicle endpoints
 * continue returning results rather than an empty fleet.
 *
 * <p><b>Speed:</b> Prasarana broadcasts the {@code speed} field in km/h. Values are
 * converted to m/s (÷ 3.6) before use. If the field is absent or outside a plausible
 * range, {@code DEFAULT_SPEED_MPS} (~11 km/h) is used as a fallback.
 *
 * <p><b>Stale eviction:</b> Vehicles not updated within {@code STALE_THRESHOLD_MS}
 * (10 minutes) are removed when a healthy feed cycle runs.
 */
@Service
public class LiveTrackingService {

    private final Map<String, VehiclePosition> activeVehicles = new ConcurrentHashMap<>();

    private final AtomicInteger[] feedBackoffCycles = {new AtomicInteger(0), new AtomicInteger(0)};

    // How many 30s cycles to skip after a 429. 4 cycles = 2 minutes backoff.
    private static final int BACKOFF_CYCLES = 4;

    // Spread the two feed requests apart to reduce back-to-back rate limit hits.
    private static final long INTER_FEED_DELAY_MS = 10000;

    // Vehicles are only evicted if they haven't been seen for this long AND at least
    // one feed is currently healthy. This prevents a full fleet wipe during a rate-limit
    // blackout where no new data arrives but the buses haven't actually stopped running.
    private static final long STALE_THRESHOLD_MS = 10 * 60 * 1000;

    // Fallback when the feed does not provide a speed value (~11 km/h).
    static final double DEFAULT_SPEED_MPS = 3.0;

    private static final String[] LIVE_FEED_URLS = {
            "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana?category=rapid-bus-kl",
            "https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana?category=rapid-bus-mrtfeeder"
    };

    /** Fetches both Prasarana feeds sequentially every 30 seconds. */
    @Scheduled(fixedRate = 30000)
    public void refreshVehiclePositions() {
        int totalIngested = 0;
        long now = System.currentTimeMillis();
        boolean anyFeedAttempted = false;

        for (int i = 0; i < LIVE_FEED_URLS.length; i++) {
            if (feedBackoffCycles[i].get() > 0) {
                int remaining = feedBackoffCycles[i].decrementAndGet();
                System.out.println("⏭️  Skipping feed (backoff, " + remaining + " cycles left): " + LIVE_FEED_URLS[i]);
                continue;
            }
            if (i > 0) {
                try { Thread.sleep(INTER_FEED_DELAY_MS); } catch (InterruptedException ignored) {}
            }
            anyFeedAttempted = true;
            totalIngested += fetchFeed(LIVE_FEED_URLS[i], feedBackoffCycles[i]);
        }

        // Only evict stale vehicles when at least one feed was attempted successfully.
        // During a full rate-limit blackout (all feeds in backoff), preserving the last
        // known positions is better than wiping the fleet and returning empty results.
        if (anyFeedAttempted) {
            evictStaleVehicles(now);
        }

        System.out.println("✅ Fleet updated: " + totalIngested + " ingested, "
                + activeVehicles.size() + " active.");
    }

    private int fetchFeed(String feedUrl, AtomicInteger backoffCount) {
        int count = 0;
        try {
            URL url = new URI(feedUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WhereBus/2.0)");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream()) {
                    FeedMessage feed = FeedMessage.parseFrom(inputStream);
                    for (FeedEntity entity : feed.getEntityList()) {
                        if (!entity.hasVehicle()) continue;
                        VehiclePosition vehicle = entity.getVehicle();
                        String vehicleId = vehicle.getVehicle().getId();
                        activeVehicles.put(vehicleId, vehicle);
                        count++;
                    }
                }
            } else if (responseCode == 429) {
                // Respect Retry-After if the server provides it (value is in seconds).
                // Convert to cycles (round up) so we wait at least as long as requested.
                // Fall back to BACKOFF_CYCLES if the header is absent or unparseable.
                int cycles = BACKOFF_CYCLES;
                String retryAfter = connection.getHeaderField("Retry-After");
                if (retryAfter != null) {
                    try {
                        int retrySeconds = Integer.parseInt(retryAfter.trim());
                        cycles = (int) Math.ceil((double) retrySeconds / 30);
                    } catch (NumberFormatException ignored) {}
                }
                backoffCount.set(cycles);
                System.err.println("⚠️  429 rate-limited: " + feedUrl
                        + " — backing off for " + cycles + " cycles ("
                        + (cycles * 30) + "s)."
                        + (retryAfter != null ? " Retry-After: " + retryAfter + "s." : ""));
            } else {
                System.err.println("⚠️  Feed returned " + responseCode + ": " + feedUrl);
            }
            connection.disconnect();
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch [" + feedUrl + "]: " + e.getMessage());
        }
        return count;
    }

    private void evictStaleVehicles(long now) {
        long staleBeforeMs = now - STALE_THRESHOLD_MS;
        activeVehicles.entrySet().removeIf(entry -> {
            VehiclePosition v = entry.getValue();
            if (!v.hasTimestamp()) return false;
            return (v.getTimestamp() * 1000L) < staleBeforeMs;
        });
    }

    /**
     * Returns the best available speed estimate for a vehicle in m/s.
     *
     * <p>Uses the feed's {@code speed} field, which Prasarana broadcasts in km/h.
     * Converted to m/s by dividing by 3.6. If the field is absent or outside a
     * plausible range (1–120 km/h), falls back to {@code DEFAULT_SPEED_MPS} (~11 km/h).
     */
    public double getSpeedMps(String vehicleId, VehiclePosition vehicle) {
        if (vehicle.getPosition().hasSpeed()) {
            double feedSpeedKmh = vehicle.getPosition().getSpeed();
            if (feedSpeedKmh > 1.0 && feedSpeedKmh < 120.0) {
                return feedSpeedKmh / 3.6;
            }
        }
        return DEFAULT_SPEED_MPS;
    }

    /**
     * Checks whether a queried route short name matches a broadcasted route ID.
     *
     * <p>Handles known Prasarana quirks:
     * <ul>
     *   <li>Case-insensitive exact match.</li>
     *   <li>Trailing-"0" appended to query: "T789" matches broadcast "T7890".</li>
     *   <li>Trailing-"0" on broadcast: "T7890" query matches broadcast "T789".</li>
     *   <li>Direction suffix on broadcast: "T155" matches "T155 Outbound".</li>
     * </ul>
     *
     * <p>{@code public static} so {@link EtaCalculationService} uses the same logic
     * without duplication.
     */
    public static boolean matchesRouteId(String queryId, String broadcastedId) {
        if (queryId.equalsIgnoreCase(broadcastedId)) return true;
        if ((queryId + "0").equalsIgnoreCase(broadcastedId)) return true;
        if (queryId.endsWith("0")
                && queryId.substring(0, queryId.length() - 1).equalsIgnoreCase(broadcastedId)) return true;
        if (broadcastedId.toLowerCase().startsWith(queryId.toLowerCase() + " ")) return true;
        return false;
    }

    /** Returns vehicles currently on the given route with GPS coordinates and direction. */
    public List<Map<String, Object>> getVehiclesByRoute(String routeId) {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        for (Map.Entry<String, VehiclePosition> entry : activeVehicles.entrySet()) {
            VehiclePosition v = entry.getValue();
            if (!v.hasTrip()) continue;
            if (!matchesRouteId(routeId, v.getTrip().getRouteId())) continue;

            int directionId = v.getTrip().hasDirectionId() ? v.getTrip().getDirectionId() : 0;

            Map<String, Object> vehicle = new HashMap<>();
            vehicle.put("vehicleId", entry.getKey());
            vehicle.put("latitude", v.getPosition().getLatitude());
            vehicle.put("longitude", v.getPosition().getLongitude());
            vehicle.put("bearing", v.getPosition().hasBearing() ? v.getPosition().getBearing() : null);
            vehicle.put("licensePlate", v.getVehicle().hasLicensePlate()
                    ? v.getVehicle().getLicensePlate() : entry.getKey());
            vehicle.put("directionId", directionId);
            vehicle.put("directionLabel", directionId == 0 ? "outbound" : "inbound");
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    /** Returns a debug snapshot of the current fleet state. */
    public Map<String, Object> getFleetSnapshot() {
        Set<String> activeRouteIds = new HashSet<>();
        List<Map<String, Object>> samples = new ArrayList<>();

        for (Map.Entry<String, VehiclePosition> entry : activeVehicles.entrySet()) {
            VehiclePosition v = entry.getValue();
            String routeId = v.hasTrip() ? v.getTrip().getRouteId() : "NO_ROUTE_ID";
            activeRouteIds.add(routeId);
            if (samples.size() < 5) {
                Map<String, Object> sample = new HashMap<>();
                sample.put("vehicleId", entry.getKey());
                sample.put("broadcastedRouteId", routeId);
                sample.put("lat", v.getPosition().getLatitude());
                sample.put("lon", v.getPosition().getLongitude());
                sample.put("feedSpeedKmh", v.getPosition().hasSpeed() ? v.getPosition().getSpeed() : null);
                samples.add(sample);
            }
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("totalActiveBuses", activeVehicles.size());
        snapshot.put("uniqueRoutesActiveNow", activeRouteIds);
        snapshot.put("sampleVehicleData", samples);
        return snapshot;
    }

    public VehiclePosition getVehicleById(String vehicleId) { return activeVehicles.get(vehicleId); }
    public Map<String, VehiclePosition> getActiveVehicles() { return activeVehicles; }
    public Set<String> getActiveVehicleIds() { return activeVehicles.keySet(); }
}
