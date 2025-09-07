import React, { useState, useEffect } from 'react';
import { initLucideIcons } from '../utils/helpers';

const ServerConfig = ({ isOpen, onClose, currentUrl, onUrlChange }) => {
    const [url, setUrl] = useState(currentUrl || 'http://localhost:8080');
    const [isConnecting, setIsConnecting] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);

    useEffect(() => {
        initLucideIcons();
    }, [isOpen, error, success]);

    useEffect(() => {
        setUrl(currentUrl || 'http://localhost:8080');
    }, [currentUrl]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsConnecting(true);
        setError(null);
        setSuccess(false);

        try {
            const response = await fetch('/api/server-config', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ jlibServerUrl: url }),
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(result.error || 'Failed to update server URL');
            }

            setSuccess(true);
            onUrlChange(url);
            
            // Auto-close after successful update
            setTimeout(() => {
                onClose();
            }, 1500);

        } catch (err) {
            setError(err.message);
        } finally {
            setIsConnecting(false);
        }
    };

    const handleReset = () => {
        setUrl('http://localhost:8080');
        setError(null);
        setSuccess(false);
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50">
            <div className="modal-overlay absolute inset-0" onClick={onClose}></div>
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div className="slide-in bg-white rounded-xl shadow-2xl max-w-md w-full overflow-hidden">
                    <div className="flex items-center justify-between p-6 border-b border-gray-200">
                        <div>
                            <h3 className="text-xl font-bold text-gray-900">Server Configuration</h3>
                            <p className="text-sm text-gray-500 mt-1">Configure JLib Server connection</p>
                        </div>
                        <button 
                            onClick={onClose}
                            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                        >
                            <i data-lucide="x" className="w-6 h-6"></i>
                        </button>
                    </div>
                    
                    <div className="p-6">
                        <form onSubmit={handleSubmit} className="space-y-4">
                            <div>
                                <label htmlFor="serverUrl" className="block text-sm font-medium text-gray-700 mb-2">
                                    JLib Server URL
                                </label>
                                <div className="relative">
                                    <input
                                        type="url"
                                        id="serverUrl"
                                        value={url}
                                        onChange={(e) => setUrl(e.target.value)}
                                        placeholder="http://localhost:8080"
                                        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                        required
                                        disabled={isConnecting}
                                    />
                                    <i data-lucide="server" className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-4 h-4"></i>
                                </div>
                                <p className="text-xs text-gray-500 mt-1">
                                    Enter the URL where your JLib Server is running
                                </p>
                            </div>

                            {error && (
                                <div className="bg-red-50 border border-red-200 rounded-lg p-3">
                                    <div className="flex items-center">
                                        <i data-lucide="alert-circle" className="w-4 h-4 text-red-500 mr-2"></i>
                                        <span className="text-sm text-red-700">{error}</span>
                                    </div>
                                </div>
                            )}

                            {success && (
                                <div className="bg-green-50 border border-green-200 rounded-lg p-3">
                                    <div className="flex items-center">
                                        <i data-lucide="check-circle" className="w-4 h-4 text-green-500 mr-2"></i>
                                        <span className="text-sm text-green-700">Server URL updated successfully!</span>
                                    </div>
                                </div>
                            )}

                            <div className="flex gap-3 pt-4">
                                <button
                                    type="button"
                                    onClick={handleReset}
                                    className="flex-1 px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
                                    disabled={isConnecting}
                                >
                                    Reset to Default
                                </button>
                                <button
                                    type="submit"
                                    disabled={isConnecting || success}
                                    className="flex-1 px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 rounded-lg transition-colors flex items-center justify-center"
                                >
                                    {isConnecting ? (
                                        <>
                                            <i data-lucide="loader" className="w-4 h-4 mr-2 animate-spin"></i>
                                            Connecting...
                                        </>
                                    ) : success ? (
                                        <>
                                            <i data-lucide="check" className="w-4 h-4 mr-2"></i>
                                            Connected
                                        </>
                                    ) : (
                                        <>
                                            <i data-lucide="plug" className="w-4 h-4 mr-2"></i>
                                            Connect
                                        </>
                                    )}
                                </button>
                            </div>
                        </form>

                        <div className="mt-6 pt-6 border-t border-gray-200">
                            <div className="text-xs text-gray-500 space-y-1">
                                <p><strong>Examples:</strong></p>
                                <p>• http://localhost:8080 (local development)</p>
                                <p>• http://192.168.1.100:8080 (network server)</p>
                                <p>• https://my-jlib-server.com (remote server)</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ServerConfig;
