import React, { useState, useEffect } from 'react';
import JarItem from './JarItem';
import { initLucideIcons, copyToClipboard } from '../utils/helpers';

const JarModal = ({ isOpen, onClose, application }) => {
    const [activeTab, setActiveTab] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [appIdCopyStatus, setAppIdCopyStatus] = useState(null);
    const [editName, setEditName] = useState('');
    const [editDescription, setEditDescription] = useState('');
    const [editTags, setEditTags] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        initLucideIcons();
    }, [isOpen, activeTab, appIdCopyStatus]);

    // Initialize edit fields when application changes (must be before any early returns to obey Rules of Hooks)
    useEffect(() => {
        if (application) {
            setEditName(application.name || '');
            setEditDescription(application.description || '');
            setEditTags((application.tags || []).join(', '));
        }
    }, [application]);

    const handleCopyAppId = async (e) => {
        e.stopPropagation();
        const success = await copyToClipboard(application.appId);
        setAppIdCopyStatus(success ? 'success' : 'error');
        setTimeout(() => setAppIdCopyStatus(null), 2000);
    };

    if (!isOpen || !application) return null;

    const handleSaveMetadata = async () => {
        setSaving(true);
        try {
            const tags = editTags.split(',').map(t => t.trim()).filter(Boolean);
            await fetch(`/api/applications/${application.appId}/metadata`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: editName, description: editDescription, tags })
            });
        } catch (e) {
            console.error('Failed to save metadata', e);
        } finally {
            setSaving(false);
        }
    };

    const loadedJars = application.jars ? application.jars.filter(jar => jar.loaded) : [];
    const notLoadedJars = application.jars ? application.jars.filter(jar => !jar.loaded) : [];

    const getTabData = () => {
        switch (activeTab) {
            case 'loaded':
                return loadedJars;
            case 'not-loaded':
                return notLoadedJars;
            default:
                return application.jars || [];
        }
    };

    const filteredJars = getTabData().filter(jar => {
        if (!searchTerm) return true;
        const term = searchTerm.toLowerCase();
        return (
            (jar.fileName && jar.fileName.toLowerCase().includes(term)) ||
            jar.path.toLowerCase().includes(term)
        );
    });

    const EmptyState = ({ type }) => {
        const configs = {
            'loaded': {
                icon: 'check-circle',
                title: 'No Loaded JARs',
                message: 'No JARs are currently loaded for this application.'
            },
            'not-loaded': {
                icon: 'circle',
                title: 'No Unloaded JARs', 
                message: 'All JARs are currently loaded for this application.'
            },
            'all': {
                icon: 'package',
                title: 'No JARs Found',
                message: 'This application has no JAR dependencies.'
            }
        };

        const config = configs[type] || configs['all'];

        return (
            <div className="text-center py-8">
                <i data-lucide={config.icon} className="w-12 h-12 text-gray-300 mx-auto mb-4"></i>
                <h3 className="text-lg font-medium text-gray-900 mb-2">{config.title}</h3>
                <p className="text-gray-500">{config.message}</p>
            </div>
        );
    };

    return (
        <div className="fixed inset-0 z-50">
            <div className="modal-overlay absolute inset-0" onClick={onClose}></div>
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div className="slide-in bg-white rounded-xl shadow-2xl max-w-4xl w-full max-h-[90vh] overflow-hidden">
                    <div className="flex items-center justify-between p-6 border-b border-gray-200">
                        <div>
                            <h3 className="text-xl font-bold text-gray-900">Java Application{application.name ? `: ${application.name}` : ''}</h3>
                            <div className="flex items-center space-x-3 mt-1">
                                <p className="text-sm text-gray-500">
                                    {application.jars ? application.jars.length : 0} dependencies for application
                                </p>
                                <div className="flex items-center space-x-2">
                                    <span className="text-sm text-gray-500 font-mono">{application.appId.substring(0, 12)}...</span>
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
                        <button 
                            onClick={onClose}
                            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                        >
                            <i data-lucide="x" className="w-6 h-6"></i>
                        </button>
                    </div>
                    
                    <div className="p-6 overflow-y-auto max-h-[70vh]">
                        {/* Application Info */}
                        <div className="bg-slate-50 rounded-lg p-4 mb-6">
                            <h4 className="font-semibold text-gray-900 mb-3">Application Details</h4>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                                <div>
                                    <span className="text-gray-600">App ID:</span>
                                    <div className="flex items-center space-x-2 mt-1">
                                        <span className="font-mono text-gray-900 text-xs break-all">{application.appId}</span>
                                        <button 
                                            onClick={handleCopyAppId}
                                            className={`flex-shrink-0 transition-colors ${
                                                appIdCopyStatus === 'success' ? 'text-green-600' :
                                                appIdCopyStatus === 'error' ? 'text-red-600' :
                                                'text-gray-400 hover:text-blue-600'
                                            }`}
                                            title="Copy Application ID"
                                        >
                                            <i data-lucide={
                                                appIdCopyStatus === 'success' ? 'check' :
                                                appIdCopyStatus === 'error' ? 'x' : 'copy'
                                            } className="w-4 h-4"></i>
                                        </button>
                                    </div>
                                </div>
                                <div>
                                    <span className="text-gray-600">Name:</span>
                                    <input
                                        type="text"
                                        value={editName}
                                        onChange={(e) => setEditName(e.target.value)}
                                        className="w-full px-2 py-1 border border-gray-300 rounded mt-1"
                                        placeholder="Friendly application name"
                                    />
                                </div>
                                <div>
                                    <span className="text-gray-600">JDK Version:</span>
                                    <span className="text-gray-900 ml-2">{application.jdkVersion} ({application.jdkVendor})</span>
                                </div>
                                <div>
                                    <span className="text-gray-600">Tags:</span>
                                    <input
                                        type="text"
                                        value={editTags}
                                        onChange={(e) => setEditTags(e.target.value)}
                                        className="w-full px-2 py-1 border border-gray-300 rounded mt-1"
                                        placeholder="tag1, tag2, tag3"
                                    />
                                </div>
                                <div className="md:col-span-2">
                                    <span className="text-gray-600">Command:</span>
                                    <div className="bg-white p-2 rounded border mt-1">
                                        <code className="text-xs break-all">{application.commandLine}</code>
                                    </div>
                                </div>
                                <div className="md:col-span-2">
                                    <span className="text-gray-600">Description:</span>
                                    <textarea
                                        value={editDescription}
                                        onChange={(e) => setEditDescription(e.target.value)}
                                        className="w-full px-2 py-1 border border-gray-300 rounded mt-1"
                                        placeholder="Short description of the app"
                                        rows={3}
                                    />
                                </div>
                                <div className="md:col-span-2 flex justify-end">
                                    <button
                                        onClick={handleSaveMetadata}
                                        disabled={saving}
                                        className={`inline-flex items-center px-3 py-2 rounded text-white ${saving ? 'bg-gray-400' : 'bg-blue-600 hover:bg-blue-700'}`}
                                    >
                                        <i data-lucide="save" className="w-4 h-4 mr-2"></i>
                                        {saving ? 'Saving...' : 'Save'}
                                    </button>
                                </div>
                            </div>
                        </div>
                        
                        {/* JAR Statistics */}
                        <div className="grid grid-cols-2 gap-4 mb-6">
                            <div className="text-center">
                                <div className="text-2xl font-bold text-green-600">{loadedJars.length}</div>
                                <div className="text-sm text-gray-600">Loaded</div>
                            </div>
                            <div className="text-center">
                                <div className="text-2xl font-bold text-gray-600">{notLoadedJars.length}</div>
                                <div className="text-sm text-gray-600">Not Loaded</div>
                            </div>
                        </div>
                        
                        {/* JAR Search */}
                        <div className="mb-4">
                            <input 
                                type="text" 
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500" 
                                placeholder="Search JARs..."
                            />
                        </div>
                        
                        {/* JAR Tabs */}
                        <div className="mb-4">
                            <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
                                <button 
                                    onClick={() => setActiveTab('all')}
                                    className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                                        activeTab === 'all' ? 'active' : ''
                                    }`}
                                >
                                    All JARs
                                </button>
                                <button 
                                    onClick={() => setActiveTab('loaded')}
                                    className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                                        activeTab === 'loaded' ? 'active' : ''
                                    }`}
                                >
                                    <span className="flex items-center justify-center">
                                        <i data-lucide="check-circle" className="w-4 h-4 mr-2"></i>
                                        Loaded ({loadedJars.length})
                                    </span>
                                </button>
                                <button 
                                    onClick={() => setActiveTab('not-loaded')}
                                    className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                                        activeTab === 'not-loaded' ? 'active' : ''
                                    }`}
                                >
                                    <span className="flex items-center justify-center">
                                        <i data-lucide="circle" className="w-4 h-4 mr-2"></i>
                                        Not Loaded ({notLoadedJars.length})
                                    </span>
                                </button>
                            </div>
                        </div>
                        
                        {/* JAR Content */}
                        <div className="space-y-2 max-h-96 overflow-y-auto">
                            {filteredJars.length > 0 ? (
                                filteredJars.map((jar, index) => (
                                    <JarItem key={index} jar={jar} />
                                ))
                            ) : getTabData().length === 0 ? (
                                <EmptyState type={activeTab} />
                            ) : (
                                <div className="text-center py-8">
                                    <i data-lucide="search" className="w-12 h-12 text-gray-300 mx-auto mb-4"></i>
                                    <h3 className="text-lg font-medium text-gray-900 mb-2">No JARs Found</h3>
                                    <p className="text-gray-500">No JARs match your search criteria.</p>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default JarModal;
