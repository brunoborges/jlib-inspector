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
            const response = await fetch('/api/dashboard');
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
            setDashboardData(prev => ({ ...prev, serverStatus: 'connected' }));
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
            setDashboardData(prev => ({ ...prev, serverStatus: 'disconnected' }));
            setTimeout(setupWebSocket, 5000);
        };
        
        websocket.onerror = (error) => {
            console.error('WebSocket error:', error);
            setDashboardData(prev => ({ ...prev, serverStatus: 'disconnected' }));
        };

        setWs(websocket);
        
        return websocket;
    }, []);

    // Manual refresh
    const refreshData = useCallback(async () => {
        setIsLoading(true);
        await fetchInitialData();
    }, [fetchInitialData]);

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
        refreshData
    };
};
