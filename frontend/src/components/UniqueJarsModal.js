import React, { useState, useEffect } from 'react';
import JarItem from './JarItem';
import { initLucideIcons } from '../utils/helpers';

const UniqueJarsModal = ({ isOpen, onClose, applications }) => {
    const [searchTerm, setSearchTerm] = useState('');
    const [sortBy, setSortBy] = useState('filename');
    const [sortOrder, setSortOrder] = useState('asc');

    useEffect(() => {
        initLucideIcons();
    }, [isOpen, sortBy, sortOrder]);

    if (!isOpen) return null;

    // Get all JARs (unique set across applications)
    const getUniqueJars = () => {
        const jarMap = new Map();
        
        applications.forEach(app => {
            if (app.jars) {
                app.jars.forEach(jar => {
                    const key = jar.fileName || jar.path.split('/').pop();
                    if (!jarMap.has(key)) {
                        jarMap.set(key, {
                            fileName: jar.fileName || jar.path.split('/').pop(),
                            path: jar.path,
                            loaded: jar.loaded,
                            size: jar.size,
                            applications: []
                        });
                    }
                    jarMap.get(key).applications.push({
                        appId: app.appId,
                        name: app.name || 'Unknown Application'
                    });
                });
            }
        });
        
        return Array.from(jarMap.values());
    };

    const uniqueJars = getUniqueJars();

    // Filter JARs based on search term
    const filteredJars = uniqueJars.filter(jar => {
        if (!searchTerm) return true;
        const term = searchTerm.toLowerCase();
        return (
            jar.fileName.toLowerCase().includes(term) ||
            jar.path.toLowerCase().includes(term) ||
            jar.applications.some(app => app.name.toLowerCase().includes(term))
        );
    });

    // Sort JARs
    const sortedJars = [...filteredJars].sort((a, b) => {
        let compareValue = 0;
        
        switch (sortBy) {
            case 'filename':
                compareValue = a.fileName.localeCompare(b.fileName);
                break;
            case 'applications':
                compareValue = a.applications.length - b.applications.length;
                break;
            case 'size':
                compareValue = (a.size || 0) - (b.size || 0);
                break;
            default:
                compareValue = a.fileName.localeCompare(b.fileName);
        }
        
        return sortOrder === 'asc' ? compareValue : -compareValue;
    });

    const handleSort = (field) => {
        if (sortBy === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortBy(field);
            setSortOrder('asc');
        }
    };

    const EmptyState = () => (
        <div className="text-center py-8">
            <i data-lucide="package" className="w-12 h-12 text-gray-300 mx-auto mb-4"></i>
            <h3 className="text-lg font-medium text-gray-900 mb-2">No JARs Found</h3>
            <p className="text-gray-500">
                {searchTerm ? 'No JARs match your search criteria.' : 'No JAR dependencies found in any application.'}
            </p>
        </div>
    );

    return (
        <div className="fixed inset-0 z-50">
            <div className="modal-overlay absolute inset-0" onClick={onClose}></div>
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div className="slide-in bg-white rounded-xl shadow-2xl max-w-6xl w-full max-h-[90vh] overflow-hidden">
                    <div className="flex items-center justify-between p-6 border-b border-gray-200">
                        <div>
                            <h3 className="text-xl font-bold text-gray-900">JAR Dependencies</h3>
                            <p className="text-sm text-gray-500 mt-1">
                                {uniqueJars.length} JARs found across {applications.length} applications
                            </p>
                        </div>
                        <button 
                            onClick={onClose}
                            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                        >
                            <i data-lucide="x" className="w-6 h-6"></i>
                        </button>
                    </div>
                    
                    <div className="p-6">
                        {/* Search and Sort Controls */}
                        <div className="flex flex-col sm:flex-row gap-4 mb-6">
                            <div className="flex-1">
                                <div className="relative">
                                    <i data-lucide="search" className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-4 h-4"></i>
                                    <input
                                        type="text"
                                        placeholder="Search JARs, paths, or applications..."
                                        value={searchTerm}
                                        onChange={(e) => setSearchTerm(e.target.value)}
                                        className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                    />
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <button
                                    onClick={() => handleSort('filename')}
                                    className={`px-3 py-2 text-sm rounded-lg transition-colors ${
                                        sortBy === 'filename' 
                                            ? 'bg-blue-100 text-blue-700' 
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                                >
                                    Name {sortBy === 'filename' && (sortOrder === 'asc' ? '↑' : '↓')}
                                </button>
                                <button
                                    onClick={() => handleSort('applications')}
                                    className={`px-3 py-2 text-sm rounded-lg transition-colors ${
                                        sortBy === 'applications' 
                                            ? 'bg-blue-100 text-blue-700' 
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                                >
                                    Apps {sortBy === 'applications' && (sortOrder === 'asc' ? '↑' : '↓')}
                                </button>
                                <button
                                    onClick={() => handleSort('size')}
                                    className={`px-3 py-2 text-sm rounded-lg transition-colors ${
                                        sortBy === 'size' 
                                            ? 'bg-blue-100 text-blue-700' 
                                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                    }`}
                                >
                                    Size {sortBy === 'size' && (sortOrder === 'asc' ? '↑' : '↓')}
                                </button>
                            </div>
                        </div>

                        {/* Results */}
                        <div className="text-sm text-gray-600 mb-4">
                            Showing {sortedJars.length} of {uniqueJars.length} JARs
                        </div>

                        {/* JAR List */}
                        <div className="overflow-y-auto max-h-[50vh]">
                            {sortedJars.length === 0 ? (
                                <EmptyState />
                            ) : (
                                <div className="space-y-3">
                                    {sortedJars.map((jar, index) => (
                                        <JarItem 
                                            key={index}
                                            jar={jar}
                                            isUniqueJar={true}
                                        />
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default UniqueJarsModal;
