'use client';

import { Search, Menu } from 'lucide-react';

export default function SearchBar({ onSearchFocus }: { onSearchFocus?: () => void }) {
  return (
    <div className="absolute top-12 left-4 right-4 z-50 max-w-md mx-auto">
      <div className="flex items-center bg-white rounded-full shadow-lg border border-gray-100 px-4 py-3">
        <Menu size={20} className="text-gray-400 mr-3 cursor-pointer" />
        <input 
          type="text" 
          placeholder="Search route (e.g. T789) or stop..." 
          className="flex-1 bg-transparent outline-none text-gray-800 placeholder-gray-400 text-sm md:text-base font-medium"
          onFocus={onSearchFocus}
        />
        <Search size={20} className="text-blue-500 ml-3 cursor-pointer" />
      </div>
      
      {/* Mock Loading Bar (Hidden by default, you can trigger this via state later) */}
      {/* <div className="h-1 bg-blue-100 w-11/12 mx-auto rounded-b-full overflow-hidden">
        <div className="h-full bg-blue-500 w-1/3 animate-pulse"></div>
      </div> */}
    </div>
  );
}