import React, { useEffect, useMemo, useState } from 'react';
import JarItem from '../components/JarItem';
import { initLucideIcons } from '../utils/helpers';

const UniqueJarsPage = ({ applications, initialFilter = 'all', onBack, onOpenApp }) => {
  const [activeTab, setActiveTab] = useState(initialFilter);
  const [searchTerm, setSearchTerm] = useState('');

  useEffect(() => {
    initLucideIcons();
  }, [activeTab]);

  useEffect(() => {
    setActiveTab(initialFilter || 'all');
  }, [initialFilter]);

  const jarMap = useMemo(() => {
    const map = new Map();
    applications.forEach(app => {
      (app.jars || []).forEach(jar => {
        const key = jar.fileName || jar.path;
        if (!map.has(key)) {
          map.set(key, { ...jar, loaded: !!jar.loaded, applications: [] });
        }
        const entry = map.get(key);
        entry.loaded = entry.loaded || !!jar.loaded; // OR across apps
        entry.applications.push({ appId: app.appId });
      });
    });
    return map;
  }, [applications]);

  const appNameById = useMemo(() => {
    const m = new Map();
    applications.forEach(a => m.set(a.appId, a.name));
    return m;
  }, [applications]);

  const uniqueJars = Array.from(jarMap.values());

  const byActivity = (list) => {
    if (activeTab === 'active') return list.filter(j => j.loaded);
    if (activeTab === 'inactive') return list.filter(j => !j.loaded);
    return list;
  };

  const filtered = byActivity(uniqueJars).filter(jar => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      (jar.fileName && jar.fileName.toLowerCase().includes(term)) ||
      (jar.path && jar.path.toLowerCase().includes(term))
    );
  }).sort((a, b) => (a.fileName || a.path).localeCompare(b.fileName || b.path));

  const counts = {
    all: uniqueJars.length,
    active: uniqueJars.filter(j => j.loaded).length,
    inactive: uniqueJars.filter(j => !j.loaded).length
  };

  return (
    <div className="min-h-screen">
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back to Dashboard
        </button>

        <div className="bg-white rounded-xl shadow p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-2xl font-bold text-gray-900">JAR Dependencies</h3>
            <div className="text-sm text-gray-500">Showing {filtered.length} of {byActivity(uniqueJars).length} unique JARs</div>
          </div>

          <div className="mb-4">
            <input 
              type="text" 
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500" 
              placeholder="Search unique JARs..."
            />
          </div>

          <div className="mb-4">
            <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg">
              <button 
                onClick={() => setActiveTab('all')}
                className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                  activeTab === 'all' ? 'active' : ''
                }`}
              >
                All ({counts.all})
              </button>
              <button 
                onClick={() => setActiveTab('active')}
                className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                  activeTab === 'active' ? 'active' : ''
                }`}
              >
                <span className="flex items-center justify-center">
                  <i data-lucide="check-circle" className="w-4 h-4 mr-2"></i>
                  Active ({counts.active})
                </span>
              </button>
              <button 
                onClick={() => setActiveTab('inactive')}
                className={`tab-button flex-1 px-4 py-2 text-sm font-medium rounded-md transition-colors ${
                  activeTab === 'inactive' ? 'active' : ''
                }`}
              >
                <span className="flex items-center justify-center">
                  <i data-lucide="circle" className="w-4 h-4 mr-2"></i>
                  Inactive ({counts.inactive})
                </span>
              </button>
            </div>
          </div>

          <div className="space-y-2">
            {filtered.length > 0 ? (
              filtered.map((jar, index) => (
                <JarItem key={index} jar={jar} isUniqueJar={true} onOpenApp={onOpenApp} appNameById={appNameById} />
              ))
            ) : (
              <div className="text-center py-8">
                <i data-lucide="search" className="w-12 h-12 text-gray-300 mx-auto mb-4"></i>
                <h3 className="text-lg font-medium text-gray-900 mb-2">No JARs Found</h3>
                <p className="text-gray-500">No JARs match your search criteria.</p>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};

export default UniqueJarsPage;
