'use client';

import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { LocateFixed } from 'lucide-react';
import 'leaflet/dist/leaflet.css';
import { Stop, Route } from '@/app/page';

// Marker
const MinimalGrayIcon = L.divIcon({
  className: 'bg-transparent',
  html: `<div style="width: 14px; height: 14px; background-color: #374151; border: 2px solid white; border-radius: 50%; box-shadow: 0 2px 5px rgba(0,0,0,0.2);"></div>`,
  iconSize: [14, 14],
  iconAnchor: [7, 7],
});

const UM_POSITION: [number, number] = [3.1209, 101.6538];

// Helper Component to Animate Map Movement
function MapUpdater({ center, zoom }: { center: [number, number], zoom: number }) {
  const map = useMap();
  useEffect(() => {
    map.flyTo(center, zoom, { duration: 1.5 });
  }, [center, zoom, map]);
  return null;
}

// Helper Component for the Recenter Button
function RecenterControl() {
  const map = useMap();
  return (
    <button 
      onClick={() => map.flyTo(UM_POSITION, 15)}
      className="absolute bottom-[55vh] right-4 z-[400] bg-white p-3 rounded-full shadow-md text-gray-600 hover:text-black transition-all border border-gray-200"
    >
      <LocateFixed size={24} />
    </button>
  );
}

interface LiveMapProps {
  selectedStop: Stop | null;
  selectedRoute: Route | null;
}

export default function LiveMap({ selectedStop, selectedRoute }: LiveMapProps) {
  // We now store the full Stop objects instead of just coordinates
  const [routeStops, setRouteStops] = useState<Stop[]>([]);

  useEffect(() => {
    if (selectedRoute) {
      fetch(`http://localhost:8080/api/transit/routes/${selectedRoute.id}/path`)
        .then(res => res.json())
        .then((data: Stop[]) => {
          setRouteStops(data); // Save the entire array of stops
        })
        .catch(err => console.error("Failed to fetch route path", err));
    } else {
      setRouteStops([]); 
    }
  }, [selectedRoute]);

  let currentCenter = UM_POSITION;
  let currentZoom = 15;

  if (selectedStop) {
    currentCenter = [selectedStop.latitude, selectedStop.longitude];
    currentZoom = 17; 
  } else if (routeStops.length > 0) {
    currentCenter = [routeStops[0].latitude, routeStops[0].longitude];
    currentZoom = 14; 
  }

  // Extract just the coordinates for the Polyline to draw the continuous path
  const polylineCoords: [number, number][] = routeStops.map(stop => [stop.latitude, stop.longitude]);

  return (
    <div className="relative w-full h-full bg-gray-100">
      <MapContainer 
        center={currentCenter} 
        zoom={currentZoom} 
        style={{ height: '100%', width: '100%', zIndex: 0 }}
        zoomControl={false} 
      >
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; OSM & CARTO'
        />
        
        <MapUpdater center={currentCenter} zoom={currentZoom} />
        
        {/* Draw the Route Line in minimalist grey */}
        {polylineCoords.length > 0 && (
          <Polyline 
            positions={polylineCoords} 
            color="#374151" // Matches the minimalist grey icon
            weight={4} 
            opacity={0.8} 
            dashArray="8, 6" // makes the line dashed to look like a transit map
          />
        )}

        {/* Render a marker for EVERY stop on the route */}
        {routeStops.map((stop, index) => (
          <Marker key={`${stop.id}-${index}`} position={[stop.latitude, stop.longitude]} icon={MinimalGrayIcon}>
            <Popup>{stop.name}</Popup>
          </Marker>
        ))}

        {/* Draw the single Selected Stop Marker (if the user searched for a specific stop) */}
        {selectedStop && (
          <Marker position={[selectedStop.latitude, selectedStop.longitude]} icon={MinimalGrayIcon}>
            <Popup>{selectedStop.name}</Popup>
          </Marker>
        )}

        {/* Only draw default UM markers if nothing is actively selected */}
        {!selectedStop && !selectedRoute && (
          <Marker position={[3.1209, 101.6538]} icon={MinimalGrayIcon}>
            <Popup>Masjid Ar-Rahman (Default)</Popup>
          </Marker>
        )}

        <RecenterControl />
      </MapContainer>
    </div>
  );
}