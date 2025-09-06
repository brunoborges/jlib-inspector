import React, { useEffect } from 'react';
import ApplicationCard from './ApplicationCard';
import { initLucideIcons } from '../utils/helpers';

const ApplicationsList = ({ 
    applications, 
    currentView, 
    filteredCount, 
    totalCount, 
    onOpenJarModal, 
    onRefresh 
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
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h2 className="text-xl font-bold text-gray-900">Java Applications</h2>
                    <p className="text-sm text-gray-500 mt-1">Monitored applications and their dependencies</p>
                </div>
                <div className="text-sm text-gray-500">
                    <span>{filteredCount}</span> of <span>{totalCount}</span> applications
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
