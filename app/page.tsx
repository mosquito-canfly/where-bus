'use client';

import { useState } from 'react';
import dynamic from 'next/dynamic';
import SearchBar from '@/components/SearchBar';
import BottomSheet from '@/components/BottomSheet';

// Dynamic import prevents Next.js SSR crashes with Leaflet's window object
const LiveMap = dynamic(() => import('@/components/LiveMap'), { 
  ssr: false,
  loading: () => (
    <div className="w-full h-full bg-gray-50 flex flex-col items-center justify-center">
      <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mb-4"></div>
      <p className="text-gray-500 font-medium">Loading Map...</p>
    </div>
  )
});

export default function Home() {
  const [isSheetOpen, setIsSheetOpen] = useState(false);

  // Simulated interaction: Opening the sheet when clicking the search bar
  const handleSearchFocus = () => {
    setIsSheetOpen(true);
  };

  return (
    <main className="relative h-screen w-screen overflow-hidden bg-gray-50 font-sans">
      
      {/* 1. Background Map Layer */}
      <div className="absolute inset-0 z-0">
        <LiveMap />
      </div>

      {/* 2. Top Foreground: Search Command Center */}
      <SearchBar onSearchFocus={handleSearchFocus} />

      {/* 3. Bottom Foreground: Interactive Data Sheet */}
      <BottomSheet 
        isOpen={isSheetOpen} 
        onClose={() => setIsSheetOpen(false)} 
      />

    </main>
  );
}