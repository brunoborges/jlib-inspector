import React, { useEffect, useMemo, useState } from 'react';
import JarItem from '../components/JarItem';
import { initLucideIcons } from '../utils/helpers';

// Global JARs view backed by /api/jars (deduplicated inventory)
// NOTE: The dashboard applications summary no longer includes per-app JAR arrays,
// so we cannot derive unique jars from applications; we query the server directly.
const UniqueJarsPage = ({ applications = [], initialFilter = 'all', onBack, onOpenApp, onOpenJar }) => {
  const [activeTab, setActiveTab] = useState(initialFilter);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [globalJars, setGlobalJars] = useState([]); // Raw payload from /api/jars

  // Fetch global jars once (could later add manual refresh button if desired)
  useEffect(() => {
    let aborted = false;
    const fetchGlobalJars = async () => {
      try {
        setLoading(true);
        const res = await fetch('/api/jars');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        if (!aborted) {
          setGlobalJars(Array.isArray(data.jars) ? data.jars : []);
          setError(null);
        }
      } catch (e) {
        if (!aborted) setError(e.message);
      } finally {
        if (!aborted) setLoading(false);
      }
    };
    fetchGlobalJars();
    return () => { aborted = true; };
  }, []);

  useEffect(() => { initLucideIcons(); }, [activeTab, globalJars.length]);
  useEffect(() => { setActiveTab(initialFilter || 'all'); }, [initialFilter]);

  // Map application names (may be empty given summary response lacks jar details)
  const appNameById = useMemo(() => {
    const m = new Map();
    applications.forEach(a => m.set(a.appId, a.name || 'Java Application'));
    return m;
  }, [applications]);

  // Transform global jars list into objects compatible with <JarItem /> & filters.
  // /api/jars element shape: { jarId, fileName, checksum, size, appCount, loadedAppCount }
  const uniqueJars = useMemo(() => globalJars.map(j => ({
    jarId: j.jarId,
    fileName: j.fileName || '(unknown)',
    path: j.fileName || j.jarId,            // Fallback path (server doesn't provide full path here)
    checksum: j.checksum,
    size: j.size,
    loaded: (j.loadedAppCount || 0) > 0,
    applications: Array.from({ length: j.appCount || 0 }, (_, idx) => ({ appId: `app-${idx}` }))
  })), [globalJars]);

  const filteredByActivity = useMemo(() => {
    if (activeTab === 'active') return uniqueJars.filter(j => j.loaded);
    if (activeTab === 'inactive') return uniqueJars.filter(j => !j.loaded);
    return uniqueJars;
  }, [uniqueJars, activeTab]);

  const filtered = useMemo(() => {
    if (!searchTerm) return [...filteredByActivity].sort((a, b) => (a.fileName).localeCompare(b.fileName));
    const term = searchTerm.toLowerCase();
    return filteredByActivity.filter(j =>
      (j.fileName && j.fileName.toLowerCase().includes(term)) ||
      (j.path && j.path.toLowerCase().includes(term)) ||
      (j.jarId && j.jarId.toLowerCase().includes(term))
    ).sort((a, b) => (a.fileName).localeCompare(b.fileName));
  }, [filteredByActivity, searchTerm]);

  const counts = useMemo(() => ({
    all: uniqueJars.length,
    active: uniqueJars.filter(j => j.loaded).length,
    inactive: uniqueJars.filter(j => !j.loaded).length
  }), [uniqueJars]);

  const handleOpenJar = (jar) => {
    // We no longer know which concrete application(s) own this jar from this endpoint alone.
    // Pass null for appId so the route can still open a generic jar detail (if supported).
    if (onOpenJar) onOpenJar(null, jar.path, 'unique');
  };

  return (
    <div className="min-h-screen">
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back to Dashboard
        </button>

        <div className="bg-white rounded-xl shadow p-6">
          {loading && (
            <div className="text-center py-10">
              <i data-lucide="loader" className="w-8 h-8 text-gray-400 animate-spin mx-auto mb-4" />
              <p className="text-gray-600">Loading global JAR inventory...</p>
            </div>
          )}
          {!loading && error && (
            <div className="text-center py-10">
              <i data-lucide="alert-triangle" className="w-8 h-8 text-red-400 mx-auto mb-4" />
              <h3 className="text-lg font-semibold text-gray-900 mb-2">Failed to load JARs</h3>
              <p className="text-sm text-gray-600 mb-4">{error}</p>
              <button
                onClick={() => { setLoading(true); setError(null); /* re-trigger effect by refetching */ fetch('/api/jars').then(r=>r.json()).then(d=>{setGlobalJars(d.jars||[]); setLoading(false);}).catch(e=>{setError(e.message); setLoading(false);}); }}
                className="px-3 py-2 text-sm rounded-md bg-blue-600 text-white hover:bg-blue-700"
              >Retry</button>
            </div>
          )}
          {!loading && !error && (
            <>
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-2xl font-bold text-gray-900">Global JAR Inventory</h3>
                <div className="text-sm text-gray-500">Showing {filtered.length} of {counts.all} unique JARs</div>
              </div>
              <div className="mb-4 flex flex-col gap-4 md:flex-row md:items-center">
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Search by name, path, or jarId..."
                />
                <div className="flex space-x-1 bg-gray-100 p-1 rounded-lg w-full md:w-auto">
                  <button
                    onClick={() => setActiveTab('all')}
                    className={`tab-button flex-1 px-3 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === 'all' ? 'active' : ''}`}
                  >All ({counts.all})</button>
                  <button
                    onClick={() => setActiveTab('active')}
                    className={`tab-button flex-1 px-3 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === 'active' ? 'active' : ''}`}
                  ><span className="flex items-center justify-center"><i data-lucide="check-circle" className="w-4 h-4 mr-1"/>Active ({counts.active})</span></button>
                  <button
                    onClick={() => setActiveTab('inactive')}
                    className={`tab-button flex-1 px-3 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === 'inactive' ? 'active' : ''}`}
                  ><span className="flex items-center justify-center"><i data-lucide="circle" className="w-4 h-4 mr-1"/>Inactive ({counts.inactive})</span></button>
                </div>
              </div>
              <div className="space-y-2">
                {filtered.length === 0 && (
                  <div className="text-center py-14">
                    <i data-lucide="search" className="w-12 h-12 text-gray-300 mx-auto mb-4" />
                    <h3 className="text-lg font-medium text-gray-900 mb-2">No JARs Found</h3>
                    <p className="text-gray-500 text-sm">Try adjusting your filters or search term.</p>
                  </div>
                )}
                {filtered.map(jar => (
                  <JarItem
                    key={jar.jarId}
                    jar={jar}
                    isUniqueJar={true}
                    appNameById={appNameById}
                    onOpenJar={() => handleOpenJar(jar)}
                  />
                ))}
              </div>
              {filtered.length > 0 && (
                <p className="text-[11px] text-gray-400 mt-4">Data sourced from /api/jars (deduplicated). Application association list is not provided in this view.</p>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
};

export default UniqueJarsPage;
