'use client';

import { Clock, Bus } from 'lucide-react';

interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function BottomSheet({ isOpen, onClose }: BottomSheetProps) {
  return (
    <div 
      className={`absolute bottom-0 left-0 right-0 z-50 bg-white rounded-t-3xl shadow-[0_-4px_20px_rgba(0,0,0,0.08)] transform transition-transform duration-300 ease-in-out ${
        isOpen ? 'translate-y-0' : 'translate-y-full'
      }`}
    >
      {/* Drag Handle Area */}
      <div 
        className="w-full flex justify-center pt-4 pb-2 cursor-pointer"
        onClick={onClose}
      >
        <div className="w-12 h-1.5 bg-gray-200 rounded-full"></div>
      </div>

      <div className="px-6 pb-8 pt-2 max-w-md mx-auto">
        <h2 className="text-xl font-bold text-gray-900 mb-1">Masjid Ar-Rahman</h2>
        <p className="text-sm text-gray-500 mb-5">Universiti Malaya • Stop ID: 100432</p>

        <div className="space-y-3">
          {/* Mock Real Data Card */}
          <div className="flex items-center justify-between p-4 bg-gray-50 border border-gray-100 rounded-2xl">
            <div className="flex items-center space-x-4">
              <div className="bg-blue-100 text-blue-700 font-bold px-3 py-1.5 rounded-lg text-sm">
                T789
              </div>
              <div>
                <p className="font-semibold text-gray-900 text-sm">LRT Universiti</p>
                <div className="flex items-center text-xs text-gray-500 mt-0.5">
                  <Bus size={12} className="mr-1" /> Approaching
                </div>
              </div>
            </div>
            <div className="text-right">
              <p className="text-lg font-bold text-blue-600">4 min</p>
              <p className="text-xs text-gray-400">10:50 AM</p>
            </div>
          </div>

          {/* Mock Skeleton Loading Card */}
          <div className="flex items-center justify-between p-4 bg-white border border-gray-100 rounded-2xl animate-pulse">
            <div className="flex items-center space-x-4">
              <div className="bg-gray-200 w-12 h-8 rounded-lg"></div>
              <div className="space-y-2">
                <div className="bg-gray-200 w-24 h-4 rounded"></div>
                <div className="bg-gray-200 w-16 h-3 rounded"></div>
              </div>
            </div>
            <div className="bg-gray-200 w-10 h-6 rounded"></div>
          </div>
        </div>
      </div>
    </div>
  );
}