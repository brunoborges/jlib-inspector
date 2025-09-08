import React, { useState, useEffect, useMemo, lazy, Suspense } from 'react';
import Header from './components/Header';
import LoadingSpinner from './components/LoadingSpinner';
import StatisticsCards from './components/StatisticsCards';
import SearchAndFilter from './components/SearchAndFilter';
import ApplicationsList from './components/ApplicationsList';
import { useDashboardData } from './hooks/useDashboardData';
import { initLucideIcons } from './utils/helpers';
import './styles/globals.css';

// Lazy load modal components to reduce initial bundle size
const JarModal = lazy(() => import('./components/JarModal'));
const UniqueJarsModal = lazy(() => import('./components/UniqueJarsModal'));
const ServerConfig = lazy(() => import('./components/ServerConfig'));

const App = () => {
    const { dashboardData, isLoading, error, refreshData } = useDashboardData();
    const [currentView, setCurrentView] = useState('grid');
    const [searchTerm, setSearchTerm] = useState('');
    const [filterType, setFilterType] = useState('all');
    const [selectedApplication, setSelectedApplication] = useState(null);
    const [showUniqueJarsModal, setShowUniqueJarsModal] = useState(false);
    const [uniqueJarsFilter, setUniqueJarsFilter] = useState('all');
    const [showServerConfig, setShowServerConfig] = useState(false);
    const [currentServerUrl, setCurrentServerUrl] = useState('http://localhost:8080');
    const [isRefreshing, setIsRefreshing] = useState(false);

    useEffect(() => {
        initLucideIcons();
    }, []);

    // Filter applications based on search and filter criteria
    const filteredApplications = useMemo(() => {
        return dashboardData.applications.filter(app => {
            // Search filter
            const matchesSearch = !searchTerm || 
                app.commandLine.toLowerCase().includes(searchTerm.toLowerCase()) ||
                (app.jars && app.jars.some(jar => 
                    jar.fileName && jar.fileName.toLowerCase().includes(searchTerm.toLowerCase())
                )) ||
                app.appId.toLowerCase().includes(searchTerm.toLowerCase()) ||
                app.jdkVersion.toLowerCase().includes(searchTerm.toLowerCase());
            
            // Type filter
            let matchesFilter = true;
            switch (filterType) {
                case 'loaded':
                    matchesFilter = app.jars && app.jars.some(jar => jar.loaded);
                    break;
                case 'recent':
                    const lastUpdate = new Date(app.lastUpdated);
                    const thirtyMinutesAgo = new Date(Date.now() - 30 * 60 * 1000);
                    matchesFilter = lastUpdate > thirtyMinutesAgo;
                    break;
                default:
                    matchesFilter = true;
            }
            
            return matchesSearch && matchesFilter;
        });
    }, [dashboardData.applications, searchTerm, filterType]);

    const handleViewToggle = () => {
        setCurrentView(prev => prev === 'grid' ? 'list' : 'grid');
    };

    const handleRefresh = async () => {
        setIsRefreshing(true);
        await refreshData();
        setIsRefreshing(false);
    };

    const handleOpenJarModal = (application) => {
        setSelectedApplication(application);
    };

    const handleCloseJarModal = () => {
        setSelectedApplication(null);
    };

    const handleOpenUniqueJarsModal = (filter = 'all') => {
        setUniqueJarsFilter(filter);
        setShowUniqueJarsModal(true);
    };

    const handleCloseUniqueJarsModal = () => {
        setShowUniqueJarsModal(false);
    };

    const handleOpenServerConfig = () => {
        setShowServerConfig(true);
    };

    const handleCloseServerConfig = () => {
        setShowServerConfig(false);
    };

    const handleServerUrlChange = (newUrl) => {
        setCurrentServerUrl(newUrl);
    };

    // Fetch current server configuration on mount
    useEffect(() => {
        const fetchServerConfig = async () => {
            try {
                const response = await fetch('/api/server-config');
                const config = await response.json();
                setCurrentServerUrl(config.jlibServerUrl);
            } catch (error) {
                console.error('Failed to fetch server config:', error);
            }
        };
        fetchServerConfig();
    }, []);

    // Handle keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                if (selectedApplication) {
                    handleCloseJarModal();
                } else if (showUniqueJarsModal) {
                    handleCloseUniqueJarsModal();
                } else if (showServerConfig) {
                    handleCloseServerConfig();
                }
            }
            if (e.ctrlKey && e.key === 'k') {
                e.preventDefault();
                // Focus search input if available
                const searchInput = document.querySelector('input[type="text"]');
                if (searchInput) searchInput.focus();
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [selectedApplication, showUniqueJarsModal, showServerConfig]);

    if (isLoading) {
        return (
            <div className="min-h-screen">
                <Header 
                    serverStatus={dashboardData.serverStatus}
                    lastUpdated={dashboardData.lastUpdated}
                    currentView={currentView}
                    onViewToggle={handleViewToggle}
                />
                <LoadingSpinner />
            </div>
        );
    }

    if (error) {
        return (
            <div className="min-h-screen">
                <Header 
                    serverStatus="disconnected"
                    lastUpdated={null}
                    currentView={currentView}
                    onViewToggle={handleViewToggle}
                />
                <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <div className="text-center py-12">
                        <i data-lucide="alert-circle" className="w-16 h-16 text-red-300 mx-auto mb-4"></i>
                        <h3 className="text-lg font-medium text-gray-900 mb-2">Error Loading Dashboard</h3>
                        <p className="text-gray-500 mb-4">{error}</p>
                        <button 
                            onClick={handleRefresh}
                            className="inline-flex items-center px-4 py-2 border border-transparent rounded-lg text-sm font-medium text-white bg-blue-600 hover:bg-blue-700"
                        >
                            <i data-lucide="refresh-cw" className="w-4 h-4 mr-2"></i>
                            Try Again
                        </button>
                    </div>
                </main>
            </div>
        );
    }

    return (
        <div className="min-h-screen">
            <Header 
                serverStatus={dashboardData.serverStatus}
                lastUpdated={dashboardData.lastUpdated}
                currentView={currentView}
                onViewToggle={handleViewToggle}
                onOpenServerConfig={handleOpenServerConfig}
            />

            <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                <SearchAndFilter 
                    searchTerm={searchTerm}
                    onSearchChange={setSearchTerm}
                    filterType={filterType}
                    onFilterChange={setFilterType}
                    onRefresh={handleRefresh}
                    isRefreshing={isRefreshing}
                />

                <StatisticsCards 
                    applications={dashboardData.applications} 
                    onUniqueJarsClick={handleOpenUniqueJarsModal}
                />

                <ApplicationsList 
                    applications={filteredApplications}
                    currentView={currentView}
                    filteredCount={filteredApplications.length}
                    totalCount={dashboardData.applications.length}
                    onOpenJarModal={handleOpenJarModal}
                    onRefresh={handleRefresh}
                />
            </main>

            <Suspense fallback={<div></div>}>
                <JarModal 
                    isOpen={!!selectedApplication}
                    onClose={handleCloseJarModal}
                    application={selectedApplication}
                />
            </Suspense>

            <Suspense fallback={<div></div>}>
                <UniqueJarsModal 
                    isOpen={showUniqueJarsModal}
                    onClose={handleCloseUniqueJarsModal}
                    applications={dashboardData.applications}
                    initialFilter={uniqueJarsFilter}
                />
            </Suspense>

            <Suspense fallback={<div></div>}>
                <ServerConfig 
                    isOpen={showServerConfig}
                    onClose={handleCloseServerConfig}
                    currentUrl={currentServerUrl}
                    onUrlChange={handleServerUrlChange}
                />
            </Suspense>
        </div>
    );
};

export default App;
