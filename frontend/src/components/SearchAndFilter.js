import React, { useEffect } from 'react';
import { initLucideIcons } from '../utils/helpers';

const SearchAndFilter = ({ 
    searchTerm, 
    onSearchChange, 
    filterType, 
    onFilterChange, 
    onRefresh, 
    isRefreshing 
}) => {
    useEffect(() => {
        initLucideIcons();
    }, []);

    return (
        <div className="card p-4 mb-6">
            <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
                <div className="flex-1 max-w-lg">
                    <div className="relative">
                        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <i data-lucide="search" className="w-5 h-5 text-gray-400"></i>
                        </div>
                        <input 
                            type="text" 
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                            className="search-input block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500" 
                            placeholder="Search applications, JARs, or command lines..."
                        />
                    </div>
                </div>
                <div className="flex items-center space-x-3">
                    <select 
                        value={filterType}
                        onChange={(e) => onFilterChange(e.target.value)}
                        className="border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-blue-500 focus:border-blue-500"
                    >
                        <option value="all">All Applications</option>
                        <option value="loaded">With Loaded JARs</option>
                        <option value="recent">Recently Updated</option>
                    </select>
                    <button 
                        onClick={onRefresh}
                        disabled={isRefreshing}
                        className="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors disabled:opacity-50"
                    >
                        <i data-lucide={isRefreshing ? "loader-2" : "refresh-cw"} className={`w-4 h-4 mr-2 ${isRefreshing ? 'animate-spin' : ''}`}></i>
                        {isRefreshing ? 'Refreshing...' : 'Refresh'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default SearchAndFilter;
