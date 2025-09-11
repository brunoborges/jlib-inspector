import React, { useEffect, useMemo, useState } from 'react';
import { initLucideIcons, copyToClipboard, formatFileSize, getMvnRepositoryUrl } from '../utils/helpers';

// Props:
// - jar: selected jar object (with path, fileName, loaded, manifest, etc.)
// - onBack: callback to navigate back
// - application: current application context (may be one of many using the jar)
// - applications: full list of applications (to compute all associations)
// - onOpenApp: callback(appId) to open an application's details
const JarDetails = ({ jar, onBack, application, applications = [], onOpenApp, origin = 'app' }) => {
  const [manifest, setManifest] = useState(jar?.manifest || {});
  const [loadingManifest, setLoadingManifest] = useState(false);
  const [manifestError, setManifestError] = useState(null);

  useEffect(() => { initLucideIcons(); }, [jar]);
  useEffect(() => {
    // Reset when jar changes
    setManifest(jar?.manifest || {});
    setManifestError(null);
    if (jar && (!jar.manifest || Object.keys(jar.manifest).length === 0) && jar.jarId) {
      setLoadingManifest(true);
      fetch(`/api/jars/${jar.jarId}`)
        .then(r => {
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          return r.json();
        })
        .then(data => {
          if (data.manifest) setManifest(data.manifest); else setManifest({});
        })
        .catch(err => setManifestError(err.message))
        .finally(() => setLoadingManifest(false));
    }
  }, [jar]);
  if (!jar) return (
    <div className="max-w-4xl mx-auto py-6">
      <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
        <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
        Back
      </button>
      <div className="bg-white rounded-lg shadow p-6">JAR not found.</div>
    </div>
  );

  if (jar.loading) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back
        </button>
        <div className="bg-white rounded-lg shadow p-6 text-center py-10">
          <i data-lucide="loader" className="w-8 h-8 text-gray-400 animate-spin mx-auto mb-4" />
          <p className="text-sm text-gray-600">Loading JAR details...</p>
        </div>
      </div>
    );
  }

  if (jar.error) {
    return (
      <div className="max-w-4xl mx-auto py-6">
        <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
          <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
          Back
        </button>
        <div className="bg-white rounded-lg shadow p-6 text-center py-10">
          <i data-lucide="alert-triangle" className="w-8 h-8 text-red-400 mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Failed to load JAR</h3>
          <p className="text-sm text-gray-600 mb-4">{jar.error}</p>
          <button
            onClick={() => window.location.hash = `#/jar/${jar.jarId}`}
            className="px-3 py-2 text-sm rounded bg-blue-600 text-white hover:bg-blue-700"
          >Retry</button>
        </div>
      </div>
    );
  }

  const mvnUrl = getMvnRepositoryUrl(jar.fileName);
  // manifest state managed above

  // Determine all applications referencing this jar (match on fileName fallback to path)
  const associatedApps = useMemo(() => {
    if (jar && Array.isArray(jar.applications) && jar.applications.length > 0) {
      return jar.applications.map(a => ({ appId: a.appId, name: a.name }));
    }
    if (!jar || !applications || applications.length === 0) return [];
    const key = jar.fileName || jar.path;
    return applications.filter(app => (app.jars || []).some(j => (j.fileName || j.path) === key))
      .map(app => ({ appId: app.appId, name: app.name }));
  }, [jar, applications]);

  const backLabel = origin === 'unique' ? 'Back to JARs' : 'Back to Application';

  return (
    <div className="max-w-5xl mx-auto py-6">
      <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
        <i data-lucide="arrow-left" className="w-4 h-4 mr-2"></i>
        {backLabel}
      </button>
      <div className="bg-white rounded-xl shadow p-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h2 className="text-2xl font-bold text-gray-900 break-all">{jar.fileName || jar.jarId || 'Unknown JAR'}</h2>
            {jar.path && <p className="text-xs text-gray-500 break-all mt-1 font-mono">{jar.path}</p>}
          </div>
          <div className="text-right text-sm">
            {jar.size && <div className="font-mono text-gray-600">{formatFileSize(jar.size)}</div>}
            <div className={`text-xs mt-1 ${jar.loaded ? 'text-green-600' : 'text-gray-400'}`}>{jar.loaded ? 'Loaded' : 'Not Loaded'}</div>
          </div>
        </div>

        {jar.checksum && jar.checksum !== '?' && (
          <div className="mb-4">
            <span className="text-xs font-semibold text-gray-600">SHA-256:</span>
            <div className="flex items-center mt-1">
              <code className="text-xs break-all bg-gray-50 p-2 rounded border flex-1">{jar.checksum}</code>
              <button onClick={() => copyToClipboard(jar.checksum)} className="ml-2 text-gray-400 hover:text-blue-600" title="Copy checksum">
                <i data-lucide="copy" className="w-4 h-4"/>
              </button>
            </div>
          </div>
        )}

        {mvnUrl && (
          <div className="mb-6">
            <a href={mvnUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center text-xs text-blue-600 hover:underline">
              <i data-lucide="external-link" className="w-3 h-3 mr-1"/>Search on mvnrepository.com
            </a>
          </div>
        )}

        <div className="grid md:grid-cols-2 gap-6">
          <div>
            <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="file-text" className="w-4 h-4 mr-1"/> Manifest</h3>
            {loadingManifest && (
              <p className="text-xs text-gray-500">Loading manifest...</p>
            )}
            {manifestError && (
              <p className="text-xs text-red-600">Failed to load manifest: {manifestError}</p>
            )}
            {!loadingManifest && !manifestError && Object.keys(manifest).length === 0 && (
              <p className="text-xs text-gray-500">No manifest attributes captured.</p>
            )}
            {!loadingManifest && !manifestError && Object.keys(manifest).length > 0 && (
              <div className="border rounded-lg divide-y">
                {Object.entries(manifest).map(([k,v]) => (
                  <div key={k} className="px-3 py-2 flex items-start justify-between hover:bg-gray-50">
                    <span className="text-xs font-medium text-gray-600 mr-3 break-all w-40 flex-shrink-0">{k}</span>
                    <span className="text-xs text-gray-900 font-mono break-all flex-1">{v}</span>
                    <button onClick={() => copyToClipboard(v)} className="ml-2 text-gray-400 hover:text-blue-600 flex-shrink-0" title="Copy value">
                      <i data-lucide="copy" className="w-3 h-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
          <div>
            <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="info" className="w-4 h-4 mr-1"/> Metadata</h3>
            <div className="space-y-2 text-xs">
              <div><span className="font-semibold text-gray-600">Top-level:</span> <span className="ml-1">{jar.path.includes('!/') ? 'No (nested)' : 'Yes'}</span></div>
              <div><span className="font-semibold text-gray-600">Nested:</span> <span className="ml-1">{jar.path.includes('!/') ? 'Yes' : 'No'}</span></div>
              {jar.path.includes('!/') && (
                <div><span className="font-semibold text-gray-600">Container:</span> <span className="ml-1 font-mono">{jar.path.split('!/')[0]}</span></div>
              )}
              <div><span className="font-semibold text-gray-600">File Name:</span> <span className="ml-1">{jar.fileName}</span></div>
              <div>
                <span className="font-semibold text-gray-600">Applications:</span>
                {associatedApps.length === 0 && (<span className="ml-1">(none)</span>)}
                {associatedApps.length > 0 && (
                  <ul className="ml-1 mt-1 space-y-1">
                    {associatedApps.map(a => (
                      <li key={a.appId} className="text-xs">
                        <a
                          href={`#/app/${a.appId}`}
                          onClick={e => { e.preventDefault(); onOpenApp && onOpenApp(a.appId); }}
                          className={`inline-flex items-center px-2 py-0.5 rounded bg-blue-50 text-blue-700 hover:underline hover:bg-blue-100 ${application && application.appId === a.appId ? 'ring-1 ring-blue-300' : ''}`}
                        >
                          <i data-lucide="app-window" className="w-3 h-3 mr-1" />
                          {a.name || a.appId}
                        </a>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default JarDetails;
