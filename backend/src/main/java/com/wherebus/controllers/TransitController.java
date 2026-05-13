package com.wherebus.controllers;

import com.wherebus.models.Route;
import com.wherebus.models.Stop;
import com.wherebus.services.EtaCalculationService;
import com.wherebus.services.LiveTrackingService;
import com.wherebus.services.TransitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST endpoints for static transit data, live vehicle positions, and ETA predictions.
 * All business logic lives in the service layer; this controller only handles routing and delegation.
 *
 * <p><b>Route ID parameter:</b> All endpoints that accept a {@code routeId} expect the
 * {@code route_id} value from routes.txt — the first column (e.g. "30000016" for MRT Feeder
 * route T815, "T7890" for rapid-bus-kl route T789). This is distinct from the short display
 * name (e.g. "T815") shown on buses. Use the /search endpoint to look up route IDs by name.
 */
@RestController
@RequestMapping("/api/transit")
@CrossOrigin(origins = "http://localhost:3000")
@Tag(name = "Transit", description = "Routes, stops, live vehicle positions, and ETA predictions.")
public class TransitController {

    private final TransitService transitService;
    private final EtaCalculationService etaCalculationService;
    private final LiveTrackingService liveTrackingService;

    public TransitController(
            TransitService transitService,
            EtaCalculationService etaCalculationService,
            LiveTrackingService liveTrackingService) {
        this.transitService = transitService;
        this.etaCalculationService = etaCalculationService;
        this.liveTrackingService = liveTrackingService;
    }

    /**
     * Sanity check: confirms TransitService is loaded and can resolve a known stop ID.
     * GET /api/transit/test
     */
    @Operation(summary = "Service health check",
            description = "Verifies TransitService is loaded by resolving a known stop ID.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/plain"))
    @GetMapping("/test")
    public String testDataLink() {
        Stop stop = transitService.getStopById("12000802");
        return stop != null
                ? "TransitService is wired up! Found stop: " + stop.getName()
                : "TransitService is wired up, but stop 12000802 is missing.";
    }

    /**
     * Returns static metadata for a route (short name, long name, headsigns).
     * GET /api/transit/routes/{routeId}
     */
    @Operation(summary = "Get route metadata",
            description = "Pass the route_id from routes.txt (first column), not the display name.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = Route.class)))
    @GetMapping("/routes/{routeId}")
    public Route getRouteDetails(
            @Parameter(description = "route_id from routes.txt.", example = "30000016")
            @PathVariable String routeId) {
        return transitService.getRouteById(routeId);
    }

    /**
     * Returns the ordered stop sequence along a route, suitable for drawing a polyline on a map.
     * Returns the outbound (direction 0) path.
     * GET /api/transit/routes/{routeId}/path
     */
    @Operation(summary = "Get route stop sequence (outbound)")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = Stop.class))))
    @GetMapping("/routes/{routeId}/path")
    public List<Stop> getRoutePath(
            @Parameter(description = "route_id from routes.txt.", example = "30000016")
            @PathVariable String routeId) {
        LinkedList<String> stopIds = transitService.getRoutePath(routeId);
        if (stopIds == null) return Collections.emptyList();

        List<Stop> path = new ArrayList<>();
        for (String stopId : stopIds) {
            Stop stop = transitService.getStopById(stopId);
            if (stop != null) path.add(stop);
        }
        return path;
    }

    /**
     * Searches stops and routes by name. Returns up to 10 results per category.
     * Use the returned route {@code id} field as the routeId parameter for other endpoints.
     * GET /api/transit/search?q=T815
     */
    @Operation(summary = "Search stops and routes by name",
            description = "Returns matching stops and routes. Use the route 'id' field in results as the routeId for /vehicles and /eta.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json"))
    @GetMapping("/search")
    public Map<String, Object> searchTransit(
            @Parameter(description = "Partial stop name or route name/number.", example = "T815")
            @RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            return Map.of("stops", List.of(), "routes", List.of());
        }
        return Map.of(
                "stops", transitService.searchStops(q),
                "routes", transitService.searchRoutes(q)
        );
    }

    /**
     * Returns active buses approaching a stop, sorted by ascending ETA.
     * Accepts either the internal route_id from routes.txt or the public broadcast ID.
     * GET /api/transit/eta?routeId=30000016&stopId=12000802
     */
    @Operation(summary = "Get real-time ETAs for a stop",
            description = "Returns approaching buses sorted by ETA. "
                    + "Pass the route_id from routes.txt (e.g. '30000016') or the broadcast ID (e.g. 'T815') — both are resolved. "
                    + "Use /search to find stop IDs by name.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json"))
    @GetMapping("/eta")
    public List<Map<String, Object>> getRealtimeEta(
            @Parameter(description = "route_id from routes.txt or broadcast route ID.", example = "30000016")
            @RequestParam String routeId,
            @Parameter(description = "stop_id from stops.txt.", example = "12000802")
            @RequestParam String stopId) {
        return etaCalculationService.getArrivalsForStop(routeId, stopId);
    }

    /**
     * Returns all live vehicles currently on the given route with GPS coordinates and direction.
     * Accepts either the internal route_id or the broadcast ID.
     * GET /api/transit/vehicles?routeId=30000016
     *
     * <p>Response fields:
     * <ul>
     *   <li>{@code directionId}: 0 = outbound, 1 = inbound (from GTFS-RT trip descriptor).</li>
     *   <li>{@code bearing}: degrees clockwise from north, null if not broadcast.</li>
     * </ul>
     */
    @Operation(summary = "Get live vehicle positions for a route",
            description = "Accepts route_id from routes.txt or broadcast route ID. "
                    + "Use /debug-fleet to see exact broadcast IDs if results are unexpectedly empty.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json"))
    @GetMapping("/vehicles")
    public List<Map<String, Object>> getLiveVehiclesForRoute(
            @Parameter(description = "route_id from routes.txt or broadcast route ID.", example = "30000016")
            @RequestParam String routeId) {
        return liveTrackingService.getVehiclesByRoute(routeId);
    }

    /**
     * Debug endpoint: shows raw Prasarana broadcast structure for the live fleet.
     * Check {@code uniqueRoutesActiveNow} to verify the exact route ID format being broadcast.
     * GET /api/transit/debug-fleet
     */
    @Operation(summary = "Debug live fleet feed",
            description = "Shows total active buses, all unique broadcasted route IDs, and 5 sample vehicles. "
                    + "Use this to verify route ID formats when /vehicles returns unexpected results.")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json"))
    @GetMapping("/debug-fleet")
    public Map<String, Object> debugActiveFleet() {
        return liveTrackingService.getFleetSnapshot();
    }
}
