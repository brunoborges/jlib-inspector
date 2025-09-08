import { useState, useEffect, useCallback } from 'react';

export const useDashboardData = () => {
    const [dashboardData, setDashboardData] = useState({
        applications: [],
        lastUpdated: null,
        serverStatus: 'unknown'
    });
    
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [ws, setWs] = useState(null);

    // Fetch initial data
    const fetchInitialData = useCallback(async () => {
        try {
            // Add cache-busting timestamp and headers
            const timestamp = new Date().getTime();
            const response = await fetch(`/api/dashboard?_t=${timestamp}`, {
                method: 'GET',
                headers: {
                    'Cache-Control': 'no-cache, no-store, must-revalidate',
                    'Pragma': 'no-cache',
                    'Expires': '0'
                }
            });
            if (response.ok) {
                const data = await response.json();
                setDashboardData(data);
                setError(null);
            } else {
                throw new Error('Failed to fetch data');
            }
        } catch (error) {
            console.error('Error fetching initial data:', error);
            setError(error.message);
        } finally {
            setIsLoading(false);
        }
    }, []);

    // Setup WebSocket connection
    const setupWebSocket = useCallback(() => {
        const websocket = new WebSocket('ws://localhost:3001');
        
        websocket.onopen = () => {
            console.log('WebSocket connected');
            // Don't override serverStatus here - it should come from the backend
        };
        
        websocket.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data);
                if (message.type === 'data-update') {
                    setDashboardData(message.data);
                }
            } catch (error) {
                console.error('Error parsing WebSocket message:', error);
            }
        };
        
        websocket.onclose = () => {
            console.log('WebSocket disconnected, attempting to reconnect...');
            // Don't override serverStatus here - WebSocket is just the transport
            setTimeout(setupWebSocket, 5000);
        };
        
        websocket.onerror = (error) => {
            console.error('WebSocket error:', error);
            // Don't override serverStatus here
        };

        setWs(websocket);
        
        return websocket;
    }, []);

    // Manual refresh
    const refreshData = useCallback(async () => {
        setIsLoading(true);
        await fetchInitialData();
    }, [fetchInitialData]);

    // Optimistically update a single application locally (immediate UI feedback)
    const updateApplication = useCallback((appId, patch) => {
        setDashboardData(prev => {
            if (!prev || !prev.applications) return prev;
            const updatedApps = prev.applications.map(app => 
                app.appId === appId ? { ...app, ...patch } : app
            );
            return {
                ...prev,
                applications: updatedApps,
                lastUpdated: new Date().toISOString()
            };
        });
    }, []);

    useEffect(() => {
        fetchInitialData();
        const websocket = setupWebSocket();
        
        return () => {
            if (websocket) {
                websocket.close();
            }
        };
    }, [fetchInitialData, setupWebSocket]);

    return {
        dashboardData,
        isLoading,
        error,
    refreshData,
    updateApplication
    };
};
