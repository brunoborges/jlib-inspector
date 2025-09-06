import React from 'react';

const LoadingSpinner = () => {
    return (
        <div className="flex items-center justify-center py-20">
            <div className="flex flex-col items-center space-y-4">
                <div className="relative">
                    <div className="w-12 h-12 border-4 border-blue-200 rounded-full animate-spin"></div>
                    <div className="absolute top-0 left-0 w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
                </div>
                <span className="text-gray-600 font-medium">Loading dashboard...</span>
            </div>
        </div>
    );
};

export default LoadingSpinner;
