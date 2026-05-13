package com.wherebus.services;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.wherebus.models.Stop;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes real-time ETAs for buses approaching a stop.
 *
 * <p><b>Vehicle matching:</b> Uses {@link LiveTrackingService#matchesRouteId} — the same
 * logic as the /vehicles endpoint — so both endpoints always return results for the same
 * set of buses.
 *
 * <p><b>Static route ID resolution:</b> The route ID the caller passes (and Prasarana
 * broadcasts) may differ from the internal GTFS route_id used as the key in our static
 * data (e.g. Prasarana broadcasts "T815" but routes.txt stores "30000016"). Before looking
 * up route paths or stop distances, we resolve the correct static key by trying the query
 * ID, the broadcasted ID, and both with a trailing "0" stripped.
 *
 * <p><b>Distance:</b> Uses pre-computed cumulative road distances from shape polylines where
 * available. Falls back to Haversine × road correction factor so the endpoint always returns
 * results even without shape coverage for a route.
 *
 * <p><b>hasPassed():</b> Compares cumulative road arc-lengths along the shape. Falls back
 * to no filtering when shape data is unavailable, preferring a slightly inaccurate result
 * over silently empty responses.
 *
 * <p><b>Speed:</b> Delegates entirely to {@link LiveTrackingService#getSpeedMps}.
 */
@Service
public class EtaCalculationService {

    private final TransitService transitService;
    private final LiveTrackingService liveTrackingService;

    // Fallback multiplier when shape-based road distance is unavailable.
    // KL's urban road geometry adds roughly 40% over crow-flies distance.
    private static final double HAVERSINE_ROAD_FACTOR = 1.4;

    public EtaCalculationService(TransitService transitService, LiveTrackingService liveTrackingService) {
        this.transitService = transitService;
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * Returns all active buses approaching the target stop on the given route,
     * sorted by ascending ETA. Buses that have already passed the stop are excluded
     * when shape data is available.
     *
     * @param routeId      Route ID as passed by the frontend. May be the internal GTFS
     *                     route_id (e.g. "30000016") or the broadcasted public ID ("T815").
     *                     Both are resolved internally.
     * @param targetStopId Stop ID as defined in stops.txt (e.g. "12000802").
     * @return Ordered list of arrival predictions, or an empty list if the stop is unknown.
     */
    public List<Map<String, Object>> getArrivalsForStop(String routeId, String targetStopId) {
        Stop targetStop = transitService.getStopById(targetStopId);
        if (targetStop == null) {
            System.err.println("⚠️  ETA: stop not found: " + targetStopId);
            return Collections.emptyList();
        }

        PriorityQueue<ArrivalPrediction> heap = new PriorityQueue<>(
                Comparator.comparingInt(ArrivalPrediction::getSecondsRemaining)
        );

        int totalOnRoute = 0;
        int droppedPassed = 0;
        int usedFallback = 0;

        for (Map.Entry<String, VehiclePosition> entry : liveTrackingService.getActiveVehicles().entrySet()) {
            VehiclePosition vehicle = entry.getValue();
            if (!vehicle.hasTrip()) continue;

            String broadcastedRouteId = vehicle.getTrip().getRouteId();
            if (!LiveTrackingService.matchesRouteId(routeId, broadcastedRouteId)) continue;

            totalOnRoute++;
            int directionId = vehicle.getTrip().hasDirectionId() ? vehicle.getTrip().getDirectionId() : 0;
            double busLat = vehicle.getPosition().getLatitude();
            double busLon = vehicle.getPosition().getLongitude();

            // Resolve the key that actually exists in our static routePaths map.
            // The caller may pass the internal GTFS ID or the broadcasted public ID —
            // we try both to find whichever was used during static data loading.
            String staticRouteId = resolveStaticRouteId(routeId, broadcastedRouteId, directionId);

            RoadPosition busPosition = projectOntoRoute(staticRouteId, directionId, busLat, busLon);
            RoadPosition targetPosition = getStopPosition(staticRouteId, directionId, targetStopId);

            double roadDistanceMeters;

            if (busPosition == null || targetPosition == null) {
                // Shape data not available for this route-direction — fall back to Haversine.
                usedFallback++;
                double straightLineMeters = haversineDistanceKm(
                        busLat, busLon, targetStop.getLatitude(), targetStop.getLongitude()) * 1000.0;
                roadDistanceMeters = straightLineMeters * HAVERSINE_ROAD_FACTOR;
            } else {
                if (busPosition.cumulativeDistKm >= targetPosition.cumulativeDistKm) {
                    droppedPassed++;
                    continue;
                }
                roadDistanceMeters = (targetPosition.cumulativeDistKm - busPosition.cumulativeDistKm) * 1000.0;
            }

            double speedMps = liveTrackingService.getSpeedMps(entry.getKey(), vehicle);
            int secondsRemaining = (int) (roadDistanceMeters / speedMps);
            String licensePlate = vehicle.getVehicle().hasLicensePlate()
                    ? vehicle.getVehicle().getLicensePlate() : entry.getKey();

            heap.offer(new ArrivalPrediction(
                    entry.getKey(), licensePlate, roadDistanceMeters, secondsRemaining, directionId));
        }

        System.out.println("ℹ️  ETA route=" + routeId + " stop=" + targetStopId
                + " → onRoute=" + totalOnRoute
                + " passed=" + droppedPassed
                + " fallback=" + usedFallback
                + " returning=" + heap.size());

        List<Map<String, Object>> arrivals = new ArrayList<>();
        while (!heap.isEmpty()) {
            ArrivalPrediction p = heap.poll();
            Map<String, Object> payload = new HashMap<>();
            payload.put("vehicleId", p.getVehicleId());
            payload.put("licensePlate", p.getLicensePlate());
            payload.put("distanceMeters", Math.round(p.getDistanceMeters()));
            payload.put("etaSeconds", p.getSecondsRemaining());
            payload.put("etaFormatted", formatEta(p.getSecondsRemaining()));
            payload.put("directionId", p.getDirectionId());
            payload.put("directionLabel", p.getDirectionId() == 0 ? "outbound" : "inbound");
            arrivals.add(payload);
        }

        return arrivals;
    }

    /**
     * Resolves the static route ID key used in routePaths and stopCumulativeDistances.
     *
     * <p>Routes are keyed by their GTFS route_id from routes.txt (e.g. "30000016" for MRT
     * Feeder, "T7890" for rapid-bus-kl). The caller may pass either the internal ID or the
     * broadcasted public ID. This method tries four candidates in order:
     * <ol>
     *   <li>The caller's routeId as-is.</li>
     *   <li>The broadcasted route ID as-is (most reliable for MRT Feeder routes where the
     *       static ID and broadcast ID differ entirely).</li>
     *   <li>The caller's routeId with a trailing "0" stripped.</li>
     *   <li>The broadcasted ID with a trailing "0" stripped.</li>
     * </ol>
     * Returns the first candidate that has a matching entry in routePaths, or the caller's
     * routeId unchanged as a last resort (will produce null positions, triggering fallback).
     */
    private String resolveStaticRouteId(String routeId, String broadcastedRouteId, int directionId) {
        String[] candidates = {
                routeId,
                broadcastedRouteId,
                routeId.endsWith("0") ? routeId.substring(0, routeId.length() - 1) : null,
                broadcastedRouteId.endsWith("0") ? broadcastedRouteId.substring(0, broadcastedRouteId.length() - 1) : null
        };

        for (String candidate : candidates) {
            if (candidate != null && transitService.getRoutePath(candidate, directionId) != null) {
                return candidate;
            }
        }
        return routeId;
    }

    /**
     * Looks up the pre-computed cumulative road distance for a stop in the given route-direction.
     * Returns null if the stop is not found in this direction's path or distances are unavailable.
     */
    private RoadPosition getStopPosition(String routeId, int directionId, String stopId) {
        LinkedList<String> path = transitService.getRoutePath(routeId, directionId);
        List<Double> distances = transitService.getStopCumulativeDistances(routeId, directionId);
        if (path == null || distances == null) return null;

        List<String> stopIds = new ArrayList<>(path);
        int idx = stopIds.indexOf(stopId);
        if (idx == -1 || idx >= distances.size()) return null;

        return new RoadPosition(distances.get(idx));
    }

    /**
     * Projects a GPS coordinate onto the route by finding the nearest stop in the path
     * and returning its pre-computed cumulative distance from the route start.
     */
    private RoadPosition projectOntoRoute(String routeId, int directionId, double busLat, double busLon) {
        LinkedList<String> path = transitService.getRoutePath(routeId, directionId);
        List<Double> distances = transitService.getStopCumulativeDistances(routeId, directionId);
        if (path == null || distances == null || path.isEmpty()) return null;

        List<String> stopIds = new ArrayList<>(path);
        int closestIdx = -1;
        double closestDist = Double.MAX_VALUE;

        for (int i = 0; i < stopIds.size(); i++) {
            Stop stop = transitService.getStopById(stopIds.get(i));
            if (stop == null) continue;
            double d = haversineDistanceKm(busLat, busLon, stop.getLatitude(), stop.getLongitude());
            if (d < closestDist) {
                closestDist = d;
                closestIdx = i;
            }
        }

        if (closestIdx == -1 || closestIdx >= distances.size()) return null;
        return new RoadPosition(distances.get(closestIdx));
    }

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String formatEta(int totalSeconds) {
        if (totalSeconds < 60) return "Arriving";
        return (totalSeconds / 60) + " min";
    }

    private static class RoadPosition {
        final double cumulativeDistKm;
        RoadPosition(double cumulativeDistKm) { this.cumulativeDistKm = cumulativeDistKm; }
    }

    private static class ArrivalPrediction {
        private final String vehicleId;
        private final String licensePlate;
        private final double distanceMeters;
        private final int secondsRemaining;
        private final int directionId;

        ArrivalPrediction(String vehicleId, String licensePlate, double distanceMeters,
                          int secondsRemaining, int directionId) {
            this.vehicleId = vehicleId;
            this.licensePlate = licensePlate;
            this.distanceMeters = distanceMeters;
            this.secondsRemaining = secondsRemaining;
            this.directionId = directionId;
        }

        String getVehicleId() { return vehicleId; }
        String getLicensePlate() { return licensePlate; }
        double getDistanceMeters() { return distanceMeters; }
        int getSecondsRemaining() { return secondsRemaining; }
        int getDirectionId() { return directionId; }
    }
}
