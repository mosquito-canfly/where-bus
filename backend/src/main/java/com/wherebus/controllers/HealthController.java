package com.wherebus.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller responsible for application health monitoring.
 * Provides basic endpoints to verify that the server is running and accessible.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Endpoint to check the health status of the backend server.
     * Useful for the Next.js frontend to verify connection before making heavy API calls.
     *
     * @return A simple confirmation string indicating the server is active.
     */
    @GetMapping("/health")
    public String checkHealth() {
        return "WhereBus API is up and running!";
    }
}