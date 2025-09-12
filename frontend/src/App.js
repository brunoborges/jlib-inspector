import React, { useState, useEffect, useMemo, lazy, Suspense } from 'react';
import Header from './components/Header';
import LoadingSpinner from './components/LoadingSpinner';
import StatisticsCards from './components/StatisticsCards';
import ApplicationsList from './components/ApplicationsList';
import { useDashboardData } from './hooks/useDashboardData';
import { initLucideIcons } from './utils/helpers';
import './styles/globals.css';

// Lazy load pages to reduce initial bundle size
const ApplicationDetails = lazy(() => import('./pages/ApplicationDetails'));
const UniqueJarsPage = lazy(() => import('./pages/UniqueJarsPage'));
const JarDetails = lazy(() => import('./pages/JarDetails'));
const ServerConfig = lazy(() => import('./components/ServerConfig'));
const HelpDialog = lazy(() => import('./components/HelpDialog'));
import ErrorBoundary from './components/ErrorBoundary';

const App = () => {
    const { dashboardData, isLoading, error, refreshData, updateApplication } = useDashboardData();
    const [currentView, setCurrentView] = useState('grid');
    const [searchTerm, setSearchTerm] = useState('');
    const [filterType, setFilterType] = useState('all');
    const [selectedApplication, setSelectedApplication] = useState(null);
    const [route, setRoute] = useState({ name: 'dashboard' }); // 'dashboard' | 'app' | 'unique' | 'jar'
    const [selectedJar, setSelectedJar] = useState(null); // legacy object (kept for potential fallback)
    const [selectedJarId, setSelectedJarId] = useState(null);
    const [jarOrigin, setJarOrigin] = useState('app'); // 'app' | 'unique'
    const [uniqueJarsFilter, setUniqueJarsFilter] = useState('all');
    const [showServerConfig, setShowServerConfig] = useState(false);
    const [currentServerUrl, setCurrentServerUrl] = useState('http://localhost:8080');
    const [showHelp, setShowHelp] = useState(false);
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

    const handleOpenAppPage = (application) => {
        setSelectedApplication(application);
        setRoute({ name: 'app' });
        window.history.pushState({ page: 'app', appId: application.appId }, '', `#/app/${application.appId}`);
    };

    const handleOpenAppPageById = (appId) => {
        const app = dashboardData.applications.find(a => a.appId === appId);
        if (app) {
            setSelectedApplication(app);
            setRoute({ name: 'app' });
            window.history.pushState({ page: 'app', appId }, '', `#/app/${appId}`);
        }
    };

    // Pass-through to update a single application's metadata in local state
    const handleLocalUpdateApp = (appId, patch) => {
        updateApplication(appId, patch);
        // keep selectedApplication in sync if it's the same app
        setSelectedApplication(prev => (prev && prev.appId === appId) ? { ...prev, ...patch } : prev);
    };

    const handleBackToDashboard = () => {
        setSelectedApplication(null);
        setSelectedJar(null);
        setSelectedJarId(null);
        setRoute({ name: 'dashboard' });
        window.history.pushState({ page: 'dashboard' }, '', '#/');
    };

    const handleOpenJarDetails = (jarId, origin = 'app', appContextAppId = null) => {
        if (!jarId) return;
        if (appContextAppId) {
            const app = dashboardData.applications.find(a => a.appId === appContextAppId);
            if (app) setSelectedApplication(app);
        }
        setSelectedJarId(jarId);
        setJarOrigin(origin);
        setRoute({ name: 'jar' });
        window.history.pushState({ page: 'jar', jarId, origin }, '', `#/jar/${jarId}`);
    };

    const handleOpenUniqueJarsPage = (filter = 'all') => {
        setUniqueJarsFilter(filter);
        setRoute({ name: 'unique' });
        window.history.pushState({ page: 'unique', filter }, '', `#/jars/${filter}`);
    };

    const handleOpenServerConfig = () => setShowServerConfig(true);
    const handleCloseServerConfig = () => setShowServerConfig(false);
    const handleOpenHelp = () => setShowHelp(true);
    const handleCloseHelp = () => setShowHelp(false);

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
                if (route.name !== 'dashboard') {
                    handleBackToDashboard();
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
    }, [route.name, showServerConfig]);

    // Basic hash-based routing (supports refresh and back/forward buttons)
    useEffect(() => {
        const applyHashRoute = () => {
            const hash = window.location.hash || '#/';
            const parts = hash.slice(2).split('/').filter(Boolean);
            if (parts[0] === 'jar' && parts[1]) {
                // New style route: #/jar/{jarId}
                const jarIdFromHash = parts[1];
                // Preserve existing origin if we're already on this JAR (prevents flipping from 'app' -> 'unique')
                const originToUse = (route.name === 'jar' && selectedJarId === jarIdFromHash) ? jarOrigin : 'unique';
                handleOpenJarDetails(jarIdFromHash, originToUse);
            } else if (parts[0] === 'app' && parts[1]) {
                // Backward compatibility: #/app/{appId}/jar/{...oldPath}
                if (parts[2] === 'jar' && parts[3]) {
                    const appId = parts[1];
                    const jarPath = decodeURIComponent(parts.slice(3).join('/'));
                    const app = dashboardData.applications.find(a => a.appId === appId);
                    if (app) {
                        const jar = (app.jars || []).find(j => j.path === jarPath);
                        if (jar && jar.jarId) {
                            handleOpenJarDetails(jar.jarId, 'app', appId);
                        }
                    }
                } else {
                    handleOpenAppPageById(parts[1]);
                }
            } else if (parts[0] === 'jars') {
                const filter = parts[1] || 'all';
                setUniqueJarsFilter(filter);
                setRoute({ name: 'unique' });
            } else {
                setRoute({ name: 'dashboard' });
            }
        };
        window.addEventListener('popstate', applyHashRoute);
        applyHashRoute();
        return () => window.removeEventListener('popstate', applyHashRoute);
    }, [dashboardData.applications, route.name, selectedJarId, jarOrigin]);

    if (isLoading) {
        return (
            <div className="min-h-screen">
                <Header 
                    serverStatus={dashboardData.serverStatus}
                    lastUpdated={dashboardData.lastUpdated}
                    currentView={currentView}
                    onViewToggle={handleViewToggle}
                />
                {/* Production Warning Banner */}
                <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4">
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                        <div className="flex">
                            <div className="flex-shrink-0">
                                <i data-lucide="alert-triangle" className="h-5 w-5 text-yellow-400"></i>
                            </div>
                            <div className="ml-3">
                                <p className="text-sm text-yellow-700">
                                    <strong>Experimental Software:</strong> This project is not production-ready and should only be used for development, testing, and evaluation purposes.
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
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
                {/* Production Warning Banner */}
                <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4">
                    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                        <div className="flex">
                            <div className="flex-shrink-0">
                                <i data-lucide="alert-triangle" className="h-5 w-5 text-yellow-400"></i>
                            </div>
                            <div className="ml-3">
                                <p className="text-sm text-yellow-700">
                                    <strong>Experimental Software:</strong> This project is not production-ready and should only be used for development, testing, and evaluation purposes.
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
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
                onOpenHelp={handleOpenHelp}
            />

            {/* Production Warning Banner */}
            <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex">
                        <div className="flex-shrink-0">
                            <i data-lucide="alert-triangle" className="h-5 w-5 text-yellow-400"></i>
                        </div>
                        <div className="ml-3">
                            <p className="text-sm text-yellow-700">
                                <strong>Experimental Software:</strong> This project is not production-ready and should only be used for development, testing, and evaluation purposes.
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">

                <StatisticsCards 
                    applications={dashboardData.applications} 
                    counts={{
                        applicationCount: dashboardData.applicationCount,
                        jarCount: dashboardData.jarCount,
                        activeJarCount: dashboardData.activeJarCount,
                        inactiveJarCount: dashboardData.inactiveJarCount
                    }}
                    onUniqueJarsClick={handleOpenUniqueJarsPage}
                    onTotalAppsClick={handleBackToDashboard}
                />

                {route.name === 'dashboard' && (
                    <ApplicationsList 
                        applications={filteredApplications}
                        currentView={currentView}
                        filteredCount={filteredApplications.length}
                        totalCount={dashboardData.applications.length}
                        onOpenJar={handleOpenAppPage}
                        onRefresh={handleRefresh}
                        searchTerm={searchTerm}
                        onSearchChange={setSearchTerm}
                        filterType={filterType}
                        onFilterChange={setFilterType}
                        isRefreshing={isRefreshing}
                    />
                )}

                {route.name === 'app' && (
                    <Suspense fallback={<div></div>}>
                        <ApplicationDetails 
                            application={selectedApplication}
                            onBack={handleBackToDashboard}
                            onLocalUpdateApp={handleLocalUpdateApp}
                            onOpenJar={(jarId) => handleOpenJarDetails(jarId, 'app', selectedApplication && selectedApplication.appId)}
                        />
                    </Suspense>
                )}

                {route.name === 'jar' && (
                    <ErrorBoundary jar={{ jarId: selectedJarId }}>
                        <Suspense fallback={<div></div>}>
                            <JarDetails 
                                jarId={selectedJarId}
                                origin={jarOrigin}
                                onOpenApp={handleOpenAppPageById}
                                onBack={() => {
                                    if (jarOrigin === 'unique') {
                                        setRoute({ name: 'unique' });
                                        window.history.pushState({ page: 'unique', filter: uniqueJarsFilter }, '', `#/jars/${uniqueJarsFilter}`);
                                    } else if (selectedApplication) {
                                        setRoute({ name: 'app' });
                                        window.history.pushState({ page: 'app', appId: selectedApplication.appId }, '', `#/app/${selectedApplication.appId}`);
                                    } else {
                                        handleBackToDashboard();
                                    }
                                }}
                            />
                        </Suspense>
                    </ErrorBoundary>
                )}

                {route.name === 'unique' && (
                    <Suspense fallback={<div></div>}>
                        <UniqueJarsPage 
                            applications={dashboardData.applications}
                            initialFilter={uniqueJarsFilter}
                            onBack={handleBackToDashboard}
                            onOpenApp={handleOpenAppPageById}
                            onOpenJar={(jarId) => handleOpenJarDetails(jarId, 'unique')}
                        />
                    </Suspense>
                )}
            </main>

            <Suspense fallback={<div></div>}>
                <ServerConfig 
                    isOpen={showServerConfig}
                    onClose={handleCloseServerConfig}
                    currentUrl={currentServerUrl}
                    onUrlChange={handleServerUrlChange}
                />
            </Suspense>
            <Suspense fallback={<div></div>}>
                <HelpDialog isOpen={showHelp} onClose={handleCloseHelp} />
            </Suspense>
        </div>
    );
};

export default App;
