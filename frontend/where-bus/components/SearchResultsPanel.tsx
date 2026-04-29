'use client';

import { MapPin, Bus } from 'lucide-react';
import { Stop, Route } from '@/app/page';

interface SearchResultsPanelProps {
  stopResults: Stop[];
  routeResults: Route[];
  onSelectStop: (stop: Stop) => void;
  onSelectRoute: (route: Route) => void;
}

export default function SearchResultsPanel({ 
  stopResults, 
  routeResults, 
  onSelectStop, 
  onSelectRoute 
}: SearchResultsPanelProps) {
  
  const isSearching = stopResults.length > 0 || routeResults.length > 0;

  return (
    <div className="absolute inset-0 z-[50] bg-white/95 backdrop-blur-sm pt-24 px-4 overflow-y-auto pb-6">
      <div className="max-w-md mx-auto space-y-6">
        
        {!isSearching && (
          <p className="text-sm text-center text-gray-400 mt-10">
            Type a route number or stop name to begin...
          </p>
        )}

        {/* Dynamic Routes Section */}
        {routeResults.length > 0 && (
          <div className="space-y-3">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider ml-2">Routes</p>
            {routeResults.map((route) => (
              <div 
                key={route.id}
                onClick={() => onSelectRoute(route)}
                className="flex items-center p-4 bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:bg-gray-50 transition-colors"
              >
                <div className="bg-blue-50 p-3 rounded-full mr-4 text-blue-600">
                  <Bus size={20} />
                </div>
                <div className="flex-1">
                  <h3 className="font-bold text-gray-900">{route.name}</h3>
                  <p className="text-xs text-gray-500 line-clamp-1">{route.longName}</p>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Dynamic Stops Section */}
        {stopResults.length > 0 && (
          <div className="space-y-3">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider ml-2">Stops</p>
            {stopResults.map((stop) => (
              <div 
                key={stop.id}
                onClick={() => onSelectStop(stop)}
                className="flex items-center p-4 bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:bg-gray-50 transition-colors"
              >
                <div className="bg-gray-100 p-3 rounded-full mr-4 text-gray-600">
                  <MapPin size={20} />
                </div>
                <div className="flex-1">
                  <h3 className="font-semibold text-gray-900">{stop.name}</h3>
                  <p className="text-xs text-gray-500">Stop ID: {stop.id}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}