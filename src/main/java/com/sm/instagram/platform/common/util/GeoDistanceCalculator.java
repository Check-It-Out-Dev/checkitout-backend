package com.sm.instagram.platform.common.util;

/**
 * Utility class for geographic distance calculations.
 *
 * Uses the Haversine formula to calculate the great-circle distance between
 * two points on Earth given their latitude and longitude coordinates.
 *
 * This is extracted from SessionSecurityService for reusability and testability.
 */
public final class GeoDistanceCalculator {

    /**
     * Earth's radius in kilometers (mean radius).
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Private constructor to prevent instantiation.
     */
    private GeoDistanceCalculator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculate the distance between two geographic points using the Haversine formula.
     *
     * The Haversine formula determines the great-circle distance between two points
     * on a sphere given their longitudes and latitudes.
     *
     * @param lat1 Latitude of point 1 in degrees (-90 to 90)
     * @param lon1 Longitude of point 1 in degrees (-180 to 180)
     * @param lat2 Latitude of point 2 in degrees (-90 to 90)
     * @param lon2 Longitude of point 2 in degrees (-180 to 180)
     * @return Distance between the two points in kilometers
     */
    public static double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) *
                   Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Check if two geographic points are within a specified radius of each other.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @param radiusKm Maximum allowed distance in kilometers
     * @return true if the distance between points is less than or equal to radiusKm
     */
    public static boolean isWithinRadius(double lat1, double lon1, double lat2, double lon2, double radiusKm) {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) <= radiusKm;
    }
}
