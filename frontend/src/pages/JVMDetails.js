import React, { useEffect, useState, useCallback } from 'react';
import { initLucideIcons, formatFileSize, copyToClipboard } from '../utils/helpers';

// Simple bar component for memory usage visualization
const MemoryBar = ({ used, committed, max, label }) => {
  const pctUsed = max > 0 ? (used / max) * 100 : (used / committed) * 100;
  const pctCommitted = max > 0 ? (committed / max) * 100 : 100;
  return (
    <div className="mb-3">
      <div className="flex justify-between text-[11px] text-gray-600 mb-1">
        <span>{label}</span>
        <span>{formatFileSize(used)} / {max > 0 ? formatFileSize(max) : formatFileSize(committed)}</span>
      </div>
      <div className="h-3 bg-gray-100 rounded overflow-hidden relative">
        <div className="absolute inset-y-0 left-0 bg-blue-200" style={{ width: pctCommitted + '%' }}></div>
        <div className="absolute inset-y-0 left-0 bg-blue-600" style={{ width: pctUsed + '%' }}></div>
      </div>
    </div>
  );
};

const Stat = ({ label, value, mono, small }) => (
  <div className="p-3 bg-white rounded border shadow-sm">
    <div className="text-[11px] uppercase tracking-wide text-gray-500 font-semibold">{label}</div>
    <div className={`mt-1 font-medium text-gray-900 ${small ? 'text-xs' : 'text-sm'} ${mono ? 'font-mono' : ''}`}>{value}</div>
  </div>
);

const JVMDetails = ({ appId, onBack }) => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchData = useCallback(async () => {
    if (!appId) return;
    setLoading(true); setError(null);
    try {
  // Fetch JVM details
  const res = await fetch(`/api/apps/${appId}/jvm`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setData(json);
    } catch (e) {
      setError(e.message || 'Failed to load JVM details');
    } finally {
      setLoading(false);
    }
  }, [appId]);

  useEffect(() => { fetchData(); }, [fetchData]);
  useEffect(() => { initLucideIcons(); }, [data]);

  const gcBadgeColor = (gcType) => {
    if (!gcType) return 'bg-gray-100 text-gray-600 border border-gray-200';
    if (gcType.includes('Z')) return 'bg-purple-50 text-purple-700 border border-purple-200';
    if (gcType.includes('Shen')) return 'bg-amber-50 text-amber-700 border border-amber-200';
    if (gcType.includes('G1')) return 'bg-green-50 text-green-700 border border-green-200';
    if (gcType.toLowerCase().includes('serial')) return 'bg-red-50 text-red-700 border border-red-200';
    return 'bg-blue-50 text-blue-700 border border-blue-200';
  };

  return (
    <div className="max-w-7xl mx-auto py-6">
      <button onClick={onBack} className="inline-flex items-center text-blue-600 hover:text-blue-700 mb-4">
        <i data-lucide="arrow-left" className="w-4 h-4 mr-2" />Back to Application
      </button>
      <div className="bg-white rounded-xl shadow p-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h2 className="text-2xl font-bold text-gray-900 flex items-center">JVM Details <span className="ml-2 text-xs font-normal text-gray-500">App {appId && appId.substring(0,12)}...</span></h2>
            <p className="text-xs text-gray-500 mt-1">Runtime snapshot of the target application's JVM (captured at last agent heartbeat).</p>
          </div>
          {data && (
            <div className={`px-2 py-1 rounded text-xs font-medium ${gcBadgeColor(data.gcType)}`}>GC: {data.gcType || 'Unknown'}</div>
          )}
        </div>

        {loading && (
          <div className="text-center py-16">
            <i data-lucide="loader" className="w-10 h-10 text-gray-300 animate-spin mx-auto mb-4" />
            <p className="text-sm text-gray-600">Loading JVM details...</p>
          </div>
        )}
        {error && !loading && (
          <div className="text-center py-16">
            <i data-lucide="alert-triangle" className="w-10 h-10 text-red-400 mx-auto mb-4" />
            <p className="text-sm text-gray-600 mb-4">{error}</p>
            <button onClick={fetchData} className="px-3 py-2 text-sm rounded bg-blue-600 text-white hover:bg-blue-700">Retry</button>
          </div>
        )}
        {!loading && !error && !data && (
          <div className="py-8 text-center text-sm text-gray-500">No JVM details available.</div>
        )}
        {data && !loading && !error && (
          <div className="space-y-8">
            {/* Top stats */}
            <div className="grid md:grid-cols-5 gap-3">
              <Stat label="GC Type" value={data.gcType || '—'} />
              <Stat label="Threads" value={`${data.threadCount} (peak ${data.peakThreadCount})`} />
              <Stat label="Classes" value={`${data.loadedClassCount}`} />
              <Stat label="CPU Load" value={(data.cpuLoad * 100).toFixed(1) + '%'} />
              <Stat label="Proc CPU" value={(data.processCpuLoad * 100).toFixed(1) + '%'} />
            </div>

            {/* Memory Overview */}
            <div>
              <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="memory-stick" className="w-4 h-4 mr-1" /> Memory</h3>
              <div className="grid md:grid-cols-2 gap-6">
                <div>
                  <h4 className="text-xs font-semibold text-gray-600 mb-2">Heap</h4>
                  {data.heapMemoryUsage && (
                    <MemoryBar 
                      used={data.heapMemoryUsage.used} 
                      committed={data.heapMemoryUsage.committed} 
                      max={data.heapMemoryUsage.max} 
                      label="Heap Usage" />
                  )}
                  <div className="grid grid-cols-2 gap-2 mt-2 text-[11px]">
                    <div className="bg-gray-50 p-2 rounded">Init: {formatFileSize(data.heapMemoryUsage.init)}</div>
                    <div className="bg-gray-50 p-2 rounded">Committed: {formatFileSize(data.heapMemoryUsage.committed)}</div>
                    <div className="bg-gray-50 p-2 rounded">Used: {formatFileSize(data.heapMemoryUsage.used)}</div>
                    <div className="bg-gray-50 p-2 rounded">Max: {data.heapMemoryUsage.max>0?formatFileSize(data.heapMemoryUsage.max):'N/A'}</div>
                  </div>
                </div>
                <div>
                  <h4 className="text-xs font-semibold text-gray-600 mb-2">Non-Heap</h4>
                  {data.nonHeapMemoryUsage && (
                    <MemoryBar 
                      used={data.nonHeapMemoryUsage.used} 
                      committed={data.nonHeapMemoryUsage.committed} 
                      max={data.nonHeapMemoryUsage.max} 
                      label="Non-Heap Usage" />
                  )}
                  <div className="grid grid-cols-2 gap-2 mt-2 text-[11px]">
                    <div className="bg-gray-50 p-2 rounded">Init: {formatFileSize(data.nonHeapMemoryUsage.init)}</div>
                    <div className="bg-gray-50 p-2 rounded">Committed: {formatFileSize(data.nonHeapMemoryUsage.committed)}</div>
                    <div className="bg-gray-50 p-2 rounded">Used: {formatFileSize(data.nonHeapMemoryUsage.used)}</div>
                    <div className="bg-gray-50 p-2 rounded">Max: {data.nonHeapMemoryUsage.max>0?formatFileSize(data.nonHeapMemoryUsage.max):'N/A'}</div>
                  </div>
                </div>
              </div>
            </div>

            {/* Memory Pools */}
            {Array.isArray(data.memoryPools) && data.memoryPools.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="layers" className="w-4 h-4 mr-1" /> Memory Pools</h3>
                <div className="border rounded divide-y max-h-96 overflow-auto text-xs">
                  {data.memoryPools.map((p, idx) => {
                    const usage = p.usage || {}; const peak = p.peakUsage || {};
                    const maxVal = usage.max > 0 ? usage.max : peak.max > 0 ? peak.max : usage.committed;
                    const usedPct = maxVal > 0 ? (usage.used / maxVal) * 100 : (usage.used / usage.committed) * 100;
                    return (
                      <div key={idx} className="p-3 hover:bg-gray-50">
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-medium text-gray-700 break-all">{p.name}</span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 border text-gray-600">{p.type}</span>
                        </div>
                        <div className="h-2 bg-gray-100 rounded overflow-hidden mb-2">
                          <div className="h-full bg-indigo-500" style={{ width: usedPct + '%' }}></div>
                        </div>
                        <div className="grid grid-cols-4 gap-2 text-[10px] text-gray-600">
                          <div><strong>Used:</strong> {formatFileSize(usage.used)}</div>
                          <div><strong>Comm:</strong> {formatFileSize(usage.committed)}</div>
                          <div><strong>Max:</strong> {usage.max>0?formatFileSize(usage.max):'N/A'}</div>
                          <div><strong>Peak:</strong> {peak.used!=null?formatFileSize(peak.used):'N/A'}</div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* CPU / OS */}
            <div>
              <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="cpu" className="w-4 h-4 mr-1" /> CPU & System</h3>
              <div className="grid md:grid-cols-4 gap-3">
                <Stat label="OS" value={data.osName} small />
                <Stat label="Arch" value={data.osArch} small />
                <Stat label="Version" value={data.osVersion} small />
                <Stat label="Load Avg" value={data.systemLoadAverage?.toFixed(2)} small />
              </div>
              <div className="grid md:grid-cols-4 gap-3 mt-3">
                <Stat label="Total Mem" value={formatFileSize(data.totalMemorySize)} small />
                <Stat label="Free Mem" value={formatFileSize(data.freeMemorySize)} small />
                <Stat label="Swap Free" value={formatFileSize(data.freeSwapSpaceSize)} small />
                <Stat label="Swap Total" value={formatFileSize(data.totalSwapSpaceSize)} small />
              </div>
            </div>

            {/* Flags */}
            {Array.isArray(data.jvmFlags) && data.jvmFlags.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-gray-800 mb-2 flex items-center"><i data-lucide="settings" className="w-4 h-4 mr-1" /> JVM Flags ({data.jvmFlags.length})</h3>
                <div className="border rounded divide-y max-h-72 overflow-auto text-xs">
                  {data.jvmFlags.slice(0,200).map((f, idx) => (
                    <div key={idx} className="px-3 py-1 flex items-center justify-between hover:bg-gray-50">
                      <div className="flex-1 min-w-0">
                        <span className="font-medium text-gray-700 break-all mr-2">{f.name}</span>
                        <span className="text-gray-500 break-all">= {f.value}</span>
                      </div>
                      <span className={`ml-2 text-[10px] px-1.5 py-0.5 rounded border ${f.origin==='ERGONOMIC'?'bg-green-50 text-green-700 border-green-200':'bg-gray-50 text-gray-600 border-gray-200'}`}>{f.origin}</span>
                    </div>
                  ))}
                </div>
                {data.jvmFlags.length > 200 && <p className="text-[10px] text-gray-400 mt-1">Showing first 200 flags.</p>}
              </div>
            )}

            {/* System Properties (collapsible) */}
            {Array.isArray(data.systemProperties) && data.systemProperties.length > 0 && (
              <details className="group">
                <summary className="cursor-pointer flex items-center text-sm font-semibold text-gray-800 mb-2 list-none">
                  <i data-lucide="list" className="w-4 h-4 mr-1" /> System Properties ({data.systemProperties.length})
                </summary>
                <div className="border rounded divide-y max-h-72 overflow-auto text-[11px] bg-white">
                  {data.systemProperties.slice(0,400).map((line, idx) => (
                    <div key={idx} className="px-3 py-1 font-mono break-all hover:bg-gray-50 flex items-center justify-between">
                      <span className="pr-2 flex-1">{line}</span>
                      <button onClick={() => copyToClipboard(line)} className="text-gray-400 hover:text-blue-600" title="Copy line"><i data-lucide="copy" className="w-3 h-3" /></button>
                    </div>
                  ))}
                </div>
                {data.systemProperties.length > 400 && <p className="text-[10px] text-gray-400 mt-1">Showing first 400 properties.</p>}
              </details>
            )}

            {/* Environment Variables */}
            {Array.isArray(data.environmentVariables) && data.environmentVariables.length > 0 && (
              <details className="group">
                <summary className="cursor-pointer flex items-center text-sm font-semibold text-gray-800 mb-2 list-none">
                  <i data-lucide="globe" className="w-4 h-4 mr-1" /> Environment Variables ({data.environmentVariables.length})
                </summary>
                <div className="border rounded divide-y max-h-64 overflow-auto text-[11px] bg-white">
                  {data.environmentVariables.map((line, idx) => (
                    <div key={idx} className="px-3 py-1 font-mono break-all hover:bg-gray-50 flex items-center justify-between">
                      <span className="pr-2 flex-1">{line}</span>
                      <button onClick={() => copyToClipboard(line)} className="text-gray-400 hover:text-blue-600" title="Copy variable"><i data-lucide="copy" className="w-3 h-3" /></button>
                    </div>
                  ))}
                </div>
              </details>
            )}

            {/* Raw JSON */}
            <details>
              <summary className="cursor-pointer text-sm font-semibold text-gray-800 mb-2">Raw JSON</summary>
              <pre className="bg-gray-900 text-gray-100 p-4 rounded text-[10px] overflow-auto max-h-96">{JSON.stringify(data, null, 2)}</pre>
            </details>
          </div>
        )}
      </div>
    </div>
  );
};

export default JVMDetails;
