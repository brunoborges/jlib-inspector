import React, { useEffect } from 'react';
import { formatRelativeTime, initLucideIcons } from '../utils/helpers';

const Header = ({ 
    serverStatus, 
    lastUpdated, 
    currentView, 
    onViewToggle,
    onOpenServerConfig 
}) => {
    useEffect(() => {
        initLucideIcons();
    }, []);

    const getStatusClass = (status) => {
        const classes = {
            'connected': 'bg-green-500',
            'disconnected': 'bg-red-500',
            'unknown': 'bg-gray-400'
        };
        return `w-3 h-3 rounded-full shadow-sm ${classes[status]}`;
    };

    const getStatusTextClass = (status) => {
        const classes = {
            'connected': 'text-green-600',
            'disconnected': 'text-red-600',
            'unknown': 'text-gray-600'
        };
        return `text-sm font-medium ${classes[status]}`;
    };

    const getStatusText = (status) => {
        const texts = {
            'connected': 'Connected',
            'disconnected': 'Disconnected',
            'unknown': 'Connecting...'
        };
        return texts[status];
    };

    return (
        <header className="bg-white shadow-sm border-b sticky top-0 z-40">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex justify-between items-center py-4">
                    <div className="flex items-center space-x-3">
                        <div className="w-10 h-10 bg-gradient-to-br from-blue-600 to-blue-700 rounded-xl flex items-center justify-center shadow-lg">
                            <i data-lucide="package" className="w-6 h-6 text-white"></i>
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900">JLib Inspector</h1>
                            <p className="text-sm text-gray-500">Java Applications & JAR Dependencies Monitor</p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-6">
                        <div className="flex items-center space-x-2">
                            <div className={getStatusClass(serverStatus)}></div>
                            <span className={getStatusTextClass(serverStatus)}>
                                {getStatusText(serverStatus)}
                            </span>
                        </div>
                        <div className="text-sm text-gray-500">
                            {lastUpdated ? `Updated ${formatRelativeTime(lastUpdated)}` : 'Never updated'}
                        </div>
                        <div className="flex items-center space-x-2">
                            <button 
                                onClick={onOpenServerConfig}
                                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors" 
                                title="Configure JLib Server Connection"
                            >
                                <i data-lucide="settings" className="w-5 h-5"></i>
                            </button>
                            <button 
                                onClick={onViewToggle}
                                className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors" 
                                title={currentView === 'grid' ? 'Switch to List View' : 'Switch to Grid View'}
                            >
                                <i data-lucide={currentView === 'grid' ? 'list' : 'layout-grid'} className="w-5 h-5"></i>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    );
};

export default Header;
