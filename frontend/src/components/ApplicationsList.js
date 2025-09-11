import React, { useEffect } from 'react';
import ApplicationCard from './ApplicationCard';
import { initLucideIcons } from '../utils/helpers';

const ApplicationsList = ({ 
    applications, 
    currentView, 
    filteredCount, 
    totalCount, 
    onOpenJarModal, 
    onRefresh,
    searchTerm,
    onSearchChange,
    filterType,
    onFilterChange,
    isRefreshing
}) => {
    useEffect(() => {
        initLucideIcons();
    }, []);

    if (applications.length === 0) {
        return (
            <div className="text-center py-12">
                <i data-lucide="server" className="w-16 h-16 text-gray-300 mx-auto mb-4"></i>
                <h3 className="text-lg font-medium text-gray-900 mb-2">No Applications Found</h3>
                <p className="text-gray-500 mb-4">No Java applications are currently being monitored.</p>
                <button 
                    onClick={onRefresh}
                    className="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
                >
                    <i data-lucide="refresh-cw" className="w-4 h-4 mr-2"></i>
                    Refresh Now
                </button>
            </div>
        );
    }

    const containerClass = currentView === 'grid' ? 'grid-view' : 'list-view space-y-4';

    return (
        <div className="card p-6">
            <div className="mb-6 space-y-4">
                <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-bold text-gray-900">Java Applications</h2>
                        <p className="text-sm text-gray-500 mt-1">Monitored applications and their dependencies</p>
                    </div>
                    <div className="flex flex-col md:flex-row md:items-center gap-3 w-full md:w-auto">
                        <div className="flex-1 md:w-80">
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <i data-lucide="search" className="w-4 h-4 text-gray-400"></i>
                                </div>
                                <input
                                    type="text"
                                    value={searchTerm}
                                    onChange={(e) => onSearchChange(e.target.value)}
                                    className="search-input block w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500 text-sm"
                                    placeholder="Search applications..."
                                />
                            </div>
                        </div>
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
                            <i data-lucide={isRefreshing ? 'loader-2' : 'refresh-cw'} className={`w-4 h-4 mr-2 ${isRefreshing ? 'animate-spin' : ''}`}></i>
                            {isRefreshing ? 'Refreshing...' : 'Refresh'}
                        </button>
                        <div className="text-sm text-gray-500 md:ml-2 md:text-right">
                            <span>{filteredCount}</span> of <span>{totalCount}</span> apps
                        </div>
                    </div>
                </div>
            </div>
            
            <div className={containerClass}>
                {applications.map((app, index) => (
                    <ApplicationCard 
                        key={app.appId} 
                        application={app} 
                        isGridView={currentView === 'grid'}
                        onOpenJarModal={onOpenJarModal}
                    />
                ))}
            </div>
        </div>
    );
};

export default ApplicationsList;
