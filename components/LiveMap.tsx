'use client';

import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import { LocateFixed } from 'lucide-react';
import 'leaflet/dist/leaflet.css';

// Fix for default Leaflet marker icons in Next.js
const DefaultIcon = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});
L.Marker.prototype.options.icon = DefaultIcon;

const UM_POSITION: [number, number] = [3.1209, 101.6538];

// Sub-component to handle the Recenter Button logic
function RecenterControl() {
  const map = useMap();
  return (
    <button 
      onClick={() => map.flyTo(UM_POSITION, 15)}
      className="absolute bottom-6 right-4 z-[400] bg-white p-3 rounded-full shadow-md text-gray-700 hover:text-black hover:bg-gray-50 transition-all border border-gray-100"
      aria-label="Center Map"
    >
      <LocateFixed size={24} />
    </button>
  );
}

export default function LiveMap() {
  return (
    <div className="relative w-full h-full bg-gray-50">
      <MapContainer 
        center={UM_POSITION} 
        zoom={15} 
        style={{ height: '100%', width: '100%', zIndex: 0 }}
        zoomControl={false} // Hide default zoom for cleaner UI
      >
        {/* CartoDB Light Tiles */}
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
        />
        
        {/* Example Mock Stops */}
        <Marker position={[3.1209, 101.6538]}><Popup>Masjid Ar-Rahman</Popup></Marker>
        <Marker position={[3.1225, 101.6550]}><Popup>Fakulti Sains</Popup></Marker>

        <RecenterControl />
      </MapContainer>
    </div>
  );
}