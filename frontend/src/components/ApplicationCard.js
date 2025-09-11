import React, { useState, useEffect } from 'react';
// Removed JarItem import because Recent JARs list is no longer shown on dashboard
import { formatRelativeTime, copyToClipboard, initLucideIcons } from '../utils/helpers';

const ApplicationCard = ({ application, isGridView, onOpenJarModal }) => {
    const [copyStatus, setCopyStatus] = useState(null);
    const [appIdCopyStatus, setAppIdCopyStatus] = useState(null);

    useEffect(() => {
        initLucideIcons();
    }, [copyStatus, appIdCopyStatus]);

    const handleCopyCommand = async (e) => {
        e.stopPropagation();
        const success = await copyToClipboard(application.commandLine);
        setCopyStatus(success ? 'success' : 'error');
        setTimeout(() => setCopyStatus(null), 2000);
    };

    const handleCopyAppId = async (e) => {
        e.stopPropagation();
        const success = await copyToClipboard(application.appId);
        setAppIdCopyStatus(success ? 'success' : 'error');
        setTimeout(() => setAppIdCopyStatus(null), 2000);
    };

    const loadedJars = application.jars ? application.jars.filter(jar => jar.loaded).length : 0;
    const totalJars = application.jars ? application.jars.length : 0;

    // Removed compact jar list rendering; dashboard no longer displays recent jars

    const displayName = (application.name && application.name.trim().length > 0)
        ? application.name.trim()
        : 'Java Application';

    return (
        <div 
            className="app-card card p-6 fade-in" 
            onClick={() => onOpenJarModal(application)}
        >
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center space-x-3">
                    <div className="w-12 h-12 bg-gradient-to-br from-orange-400 to-orange-600 rounded-lg flex items-center justify-center shadow-lg">
                        <i data-lucide="coffee" className="w-6 h-6 text-white"></i>
                    </div>
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900" title={displayName}>{displayName}</h3>
                        <div className="flex items-center space-x-2">
                            <p className="text-sm text-gray-500 font-mono">{application.appId.substring(0, 12)}...</p>
                            <button 
                                onClick={handleCopyAppId}
                                className={`text-xs transition-colors ${
                                    appIdCopyStatus === 'success' ? 'text-green-600' :
                                    appIdCopyStatus === 'error' ? 'text-red-600' :
                                    'text-gray-400 hover:text-blue-600'
                                }`}
                                title="Copy full Application ID"
                            >
                                <i data-lucide={
                                    appIdCopyStatus === 'success' ? 'check' :
                                    appIdCopyStatus === 'error' ? 'x' : 'copy'
                                } className="w-3 h-3"></i>
                            </button>
                        </div>
                    </div>
                </div>
                <div className="text-right">
                    <div className="flex items-center space-x-1 text-sm">
                        <span className="text-gray-600">{loadedJars}</span>
                        <span className="text-gray-400">/</span>
                        <span className="font-medium text-gray-900">{totalJars}</span>
                        <span className="text-gray-500">JARs</span>
                    </div>
                    <p className="text-xs text-gray-500">JDK {application.jdkVersion}</p>
                </div>
            </div>
            
            <div className="mb-4">
                <div className="flex items-center justify-between mb-2">
                    <p className="text-sm font-medium text-gray-700">Command Line</p>
                    <button 
                        onClick={handleCopyCommand}
                        className={`text-xs flex items-center transition-colors ${
                            copyStatus === 'success' ? 'text-green-600' :
                            copyStatus === 'error' ? 'text-red-600' :
                            'text-blue-600 hover:text-blue-800'
                        }`}
                    >
                        <i data-lucide={
                            copyStatus === 'success' ? 'check' :
                            copyStatus === 'error' ? 'x' : 'copy'
                        } className="w-3 h-3 mr-1"></i>
                        {copyStatus === 'success' ? 'Copied!' : 
                         copyStatus === 'error' ? 'Failed' : 'Copy'}
                    </button>
                </div>
                <div className="bg-gray-50 p-3 rounded-lg border">
                    <p className="text-xs text-gray-800 font-mono break-all leading-relaxed line-clamp-2">
                        {application.commandLine}
                    </p>
                </div>
            </div>
            
            {/* Recent JARs section removed per data minimization & lazy loading strategy */}
            
            <div className="flex items-center justify-between text-xs text-gray-500">
                <span>Updated {formatRelativeTime(application.lastUpdated)}</span>
                <span className="text-blue-600 font-medium">Click to view all JARs →</span>
            </div>
        </div>
    );
};

export default ApplicationCard;
