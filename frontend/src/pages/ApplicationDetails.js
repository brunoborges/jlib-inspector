import React, { useState, useEffect } from 'react';
import JarItem from '../components/JarItem';
import { initLucideIcons, copyToClipboard } from '../utils/helpers';

const ApplicationDetails = ({ application, onBack, onLocalUpdateApp, onOpenJar, onOpenJvm }) => {
  const [activeTab, setActiveTab] = useState('all');
  const [searchTerm, setSearchTerm] = useState('');
  const [appIdCopyStatus, setAppIdCopyStatus] = useState(null);
  const [editName, setEditName] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editTags, setEditTags] = useState('');
  const [saving, setSaving] = useState(false);
  // Lazy-loaded jars fetched from server (dashboard no longer supplies them inline)
  const [jars, setJars] = useState([]);
  const [jarsLoading, setJarsLoading] = useState(false);
  const [jarsError, setJarsError] = useState(null);

  useEffect(() => {
    initLucideIcons();
  }, [activeTab, appIdCopyStatus, jarsLoading, jarsError, jars.length]);

  // Initialize edit fields & fetch jars when application changes
  useEffect(() => {
    if (application) {
      setEditName(application.name || '');
      setEditDescription(application.description || '');
      setEditTags((application.tags || []).join(', '));
    }
  }, [application]);

  useEffect(() => {
    if (!application || !application.appId) {
      setJars([]);
      return;
    }
    let aborted = false;
    const controller = new AbortController();
  const fetchJars = async () => {
      try {
        setJarsLoading(true);
        setJarsError(null);
  // Fetch JARs for application
  const res = await fetch(`/api/apps/${application.appId}/jars`, { signal: controller.signal });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (!aborted) {
            const list = Array.isArray(data.jars) ? data.jars : Array.isArray(data) ? data : [];
            setJars(list);
        }
      } catch (e) {
        if (!aborted && e.name !== 'AbortError') setJarsError(e.message);
      } finally {
        if (!aborted) setJarsLoading(false);
      }
    };
    fetchJars();
    return () => { aborted = true; controller.abort(); };
  }, [application && application.appId]);

  if (!application) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back to Dashboard
        </button>
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold text-gray-900">Application not found</h2>
          <p className="text-gray-600 mt-2">The requested application does not exist or hasn’t reported yet.</p>
        </div>
      </div>
    );
  }

  const handleCopyAppId = async () => {
    const success = await copyToClipboard(application.appId);
    setAppIdCopyStatus(success ? 'success' : 'error');
    setTimeout(() => setAppIdCopyStatus(null), 2000);
  };

  const handleSaveMetadata = async () => {
    setSaving(true);
    try {
      const tags = editTags.split(',').map(t => t.trim()).filter(Boolean);
  await fetch(`/api/apps/${application.appId}/metadata`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: editName, description: editDescription, tags })
      });
      // Optimistically update local state so other pages reflect changes immediately
      if (onLocalUpdateApp) {
        onLocalUpdateApp(application.appId, { name: editName, description: editDescription, tags });
      }
    } catch (e) {
      console.error('Failed to save metadata', e);
    } finally {
      setSaving(false);
    }
  };

  const loadedJars = jars.filter(jar => jar.loaded);
  const notLoadedJars = jars.filter(jar => !jar.loaded);

  const getTabData = () => {
    switch (activeTab) {
      case 'loaded':
        return loadedJars;
      case 'not-loaded':
        return notLoadedJars;
      default:
    return jars;
    }
  };

  const filteredJars = getTabData().filter(jar => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      (jar.fileName && jar.fileName.toLowerCase().includes(term)) ||
      (jar.path && jar.path.toLowerCase().includes(term))
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
    <div className="min-h-screen">
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back to Dashboard
        </button>

        <div className="bg-white rounded-xl shadow p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h3 className="text-2xl font-bold text-gray-900">Application Details{application.name ? `: ${application.name}` : ''}</h3>
            </div>
            {onOpenJvm && (
              <button onClick={() => onOpenJvm()} className="inline-flex items-center px-3 py-2 text-sm rounded bg-indigo-600 text-white hover:bg-indigo-700">
                <i data-lucide="cpu" className="w-4 h-4 mr-2" /> JVM Details
              </button>
            )}
          </div>

          {/* Application Info */}
          <div className="bg-slate-50 rounded-lg p-4 mb-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <div>
                <span className="text-gray-600">App ID:</span>
                <div className="flex items-center space-x-2 mt-1">
                  <span className="font-mono text-gray-900 text-xs break-all">{application.appId.substring(0, 12)}...</span>
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

          {/* JAR Section Header */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h3 className="text-2xl font-bold text-gray-900">JARs</h3>
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
          <div className="space-y-2">
            {jarsLoading && (
              <div className="text-center py-8">
                <i data-lucide="loader" className="w-8 h-8 text-gray-300 animate-spin mx-auto mb-4"></i>
                <h3 className="text-lg font-medium text-gray-900 mb-2">Loading JARs...</h3>
                <p className="text-gray-500">Fetching dependencies for this application.</p>
              </div>
            )}
            {!jarsLoading && jarsError && (
              <div className="text-center py-8">
                <i data-lucide="alert-triangle" className="w-8 h-8 text-red-400 mx-auto mb-4"></i>
                <h3 className="text-lg font-medium text-gray-900 mb-2">Failed to load JARs</h3>
                <p className="text-gray-500 mb-4 text-sm">{jarsError}</p>
                <button
                  onClick={() => {
                    // retrigger fetch by resetting dependency (appId stays same, so call directly)
                    if (application && application.appId) {
                      setJarsLoading(true);
                      fetch(`/api/apps/${application.appId}/jars`)
                        .then(r => { if(!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
                        .then(d => { const list = Array.isArray(d.jars) ? d.jars : Array.isArray(d) ? d : []; setJars(list); setJarsError(null); })
                        .catch(e => setJarsError(e.message))
                        .finally(() => setJarsLoading(false));
                    }
                  }}
                  className="px-3 py-2 rounded bg-blue-600 text-white text-sm hover:bg-blue-700"
                >Retry</button>
              </div>
            )}
            {!jarsLoading && !jarsError && (
              filteredJars.length > 0 ? (
                filteredJars.map((jar, index) => (
                  <JarItem 
                    key={index} 
                    jar={jar} 
                    onOpenJar={() => onOpenJar && jar.jarId && onOpenJar(jar.jarId)}
                  />
                ))
              ) : getTabData().length === 0 ? (
                <EmptyState type={activeTab} />
              ) : (
                <div className="text-center py-8">
                  <i data-lucide="search" className="w-12 h-12 text-gray-300 mx-auto mb-4"></i>
                  <h3 className="text-lg font-medium text-gray-900 mb-2">No JARs Found</h3>
                  <p className="text-gray-500">No JARs match your search criteria.</p>
                </div>
              )
            )}
          </div>
          {!jarsLoading && !jarsError && filteredJars.length > 0 && (
            <p className="text-[11px] text-gray-400 mt-4">Tip: Click a JAR or the Details link to open its manifest and metadata.</p>
          )}
        </div>
      </main>
    </div>
  );
};

export default ApplicationDetails;
