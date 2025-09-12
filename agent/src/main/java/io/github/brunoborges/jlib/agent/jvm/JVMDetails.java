package io.github.brunoborges.jlib.agent.jvm;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.brunoborges.jlib.agent.jvm.IdentifyGC.GCType;
import io.github.brunoborges.jlib.agent.jvm.PrintFlagsFinal.JVMFlag;

public class JVMDetails {

    String id;
    private List<String> systemProperties;
    private List<String> environmentVariables;
    private double processCpuLoad;
    private long freeSwapSpaceSize;
    private double cpuLoad;
    private long processCpuTime;
    private List<String> garbageCollectors;
    private List<JVMFlag> jvmFlags;
    private String compilerName;
    private long totalCompilationTime;
    private int loadedClassCount;
    private long unloadedClassCount;
    private long totalLoadedClassCount;
    private List<String> inputArguments;
    private String vmVendor;
    private String vmVersion;
    private String vmName;
    private String pidHostname;
    private MemoryUsage heapMemoryUsage;
    private MemoryUsage nonHeapMemoryUsage;
    private List<MemoryPoolMXBean> memoryPoolMXBeans;
    private int threadCount;
    private int peakThreadCount;
    private long totalStartedThreadCount;
    private String osName;
    private String osVersion;
    private String osArch;
    private double systemLoadAverage;
    private long freeMemorySize;
    private long totalMemorySize;
    private long committedVirtualMemory;
    private long totalSwapSpaceSize;
    private int availableProcessors;
    private GCType gcType;

    public JVMDetails() {
    }

    public void jvmId(String name) {
        this.id = name;
    }

    public void systemProperties(List<String> collect) {
        this.systemProperties = collect;
    }

    public void environmentVariables(List<String> collect) {
        this.environmentVariables = collect;
    }

    public void garbageCollectors(List<String> collect) {
        this.garbageCollectors = collect;
    }

    public void processCpuTime(long processCpuTime) {
        this.processCpuTime = processCpuTime;
    }

    public void cpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public void freeSwapSpaceSize(long freeSwapSpaceSize) {
        this.freeSwapSpaceSize = freeSwapSpaceSize;
    }

    public void processCpuTime(double processCpuLoad) {
        this.processCpuLoad = processCpuLoad;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the systemProperties
     */
    public List<String> getSystemProperties() {
        return systemProperties;
    }

    /**
     * @return the environmentVariables
     */
    public List<String> getEnvironmentVariables() {
        return environmentVariables;
    }

    /**
     * @return the processCpuLoad
     */
    public double getProcessCpuLoad() {
        return processCpuLoad;
    }

    /**
     * @return the freeSwapSpaceSize
     */
    public long getFreeSwapSpaceSize() {
        return freeSwapSpaceSize;
    }

    /**
     * @return the cpuLoad
     */
    public double getCpuLoad() {
        return cpuLoad;
    }

    /**
     * @return the processCpuTime
     */
    public long getProcessCpuTime() {
        return processCpuTime;
    }

    /**
     * @return the garbageCollectors
     */
    public List<String> getGarbageCollectors() {
        return garbageCollectors;
    }

    /**
     * @return the jvmFlags
     */
    public List<JVMFlag> getJvmFlags() {
        return jvmFlags;
    }

    public void compilerName(String name) {
        this.compilerName = name;
    }

    public void totalCompilationTime(long totalCompilationTime) {
        this.totalCompilationTime = totalCompilationTime;
    }

    public void loadedClassCount(int loadedClassCount) {
        this.loadedClassCount = loadedClassCount;
    }

    public void unloadedClassCount(long unloadedClassCount) {
        this.unloadedClassCount = unloadedClassCount;
    }

    public void totalLoadedClassCount(long totalLoadedClassCount) {
        this.totalLoadedClassCount = totalLoadedClassCount;
    }

    public void inputArguments(List<String> inputArguments) {
        this.inputArguments = inputArguments;
    }

    public void vmVendor(String vmVendor) {
        this.vmVendor = vmVendor;
    }

    public void vmVersion(String vmVersion) {
        this.vmVersion = vmVersion;
    }

    public void vmName(String vmName) {
        this.vmName = vmName;
    }

    public void pidHostname(String name) {
        this.pidHostname = name;
    }

    public void heapMemoryUsage(MemoryUsage heapMemoryUsage) {
        this.heapMemoryUsage = heapMemoryUsage;
    }

    public void nonHeapMemoryUsage(MemoryUsage nonHeapMemoryUsage) {
        this.nonHeapMemoryUsage = nonHeapMemoryUsage;
    }

    public void memoryPoolMXBeans(List<MemoryPoolMXBean> memoryPoolMXBeans) {
        this.memoryPoolMXBeans = memoryPoolMXBeans;
    }

    public void threadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public void peakThreadCount(int peakThreadCount) {
        this.peakThreadCount = peakThreadCount;
    }

    public void totalStartedThreadCount(long totalStartedThreadCount) {
        this.totalStartedThreadCount = totalStartedThreadCount;
    }

    public void osName(String name) {
        this.osName = name;
    }

    public void osVersion(String version) {
        this.osVersion = version;
    }

    public void osArch(String arch) {
        this.osArch = arch;
    }

    /**
     * @return the compilerName
     */
    public String getCompilerName() {
        return compilerName;
    }

    /**
     * @return the totalCompilationTime
     */
    public long getTotalCompilationTime() {
        return totalCompilationTime;
    }

    /**
     * @return the loadedClassCount
     */
    public int getLoadedClassCount() {
        return loadedClassCount;
    }

    /**
     * @return the unloadedClassCount
     */
    public long getUnloadedClassCount() {
        return unloadedClassCount;
    }

    /**
     * @return the totalLoadedClassCount
     */
    public long getTotalLoadedClassCount() {
        return totalLoadedClassCount;
    }

    /**
     * @return the inputArguments
     */
    public List<String> getInputArguments() {
        return inputArguments;
    }

    /**
     * @return the vmVendor
     */
    public String getVmVendor() {
        return vmVendor;
    }

    /**
     * @return the vmVersion
     */
    public String getVmVersion() {
        return vmVersion;
    }

    /**
     * @return the vmName
     */
    public String getVmName() {
        return vmName;
    }

    /**
     * @return the pidHostname
     */
    public String getPidHostname() {
        return pidHostname;
    }

    /**
     * @return the heapMemoryUsage
     */
    public MemoryUsage getHeapMemoryUsage() {
        return heapMemoryUsage;
    }

    /**
     * @return the nonHeapMemoryUsage
     */
    public MemoryUsage getNonHeapMemoryUsage() {
        return nonHeapMemoryUsage;
    }

    /**
     * @return the memoryPoolMXBeans
     */
    public List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        return memoryPoolMXBeans;
    }

    /**
     * @return the threadCount
     */
    public int getThreadCount() {
        return threadCount;
    }

    /**
     * @return the peakThreadCount
     */
    public int getPeakThreadCount() {
        return peakThreadCount;
    }

    /**
     * @return the totalStartedThreadCount
     */
    public long getTotalStartedThreadCount() {
        return totalStartedThreadCount;
    }

    /**
     * @return the osName
     */
    public String getOsName() {
        return osName;
    }

    /**
     * @return the osVersion
     */
    public String getOsVersion() {
        return osVersion;
    }

    /**
     * @return the osArch
     */
    public String getOsArch() {
        return osArch;
    }

    /**
     * @return the systemLoadAverage
     */
    public double getSystemLoadAverage() {
        return systemLoadAverage;
    }

    /**
     * @return the freeMemorySize
     */
    public long getFreeMemorySize() {
        return freeMemorySize;
    }

    /**
     * @return the totalMemorySize
     */
    public long getTotalMemorySize() {
        return totalMemorySize;
    }

    /**
     * @return the committedVirtualMemory
     */
    public long getCommittedVirtualMemory() {
        return committedVirtualMemory;
    }

    /**
     * @return the totalSwapSpaceSize
     */
    public long getTotalSwapSpaceSize() {
        return totalSwapSpaceSize;
    }

    /**
     * @return the availableProcessors
     */
    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public void availableProcessors(int availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public void systemLoadAverage(double systemLoadAverage) {
        this.systemLoadAverage = systemLoadAverage;
    }

    public void committedVirtualMemory(long committedVirtualMemorySize) {
        this.committedVirtualMemory = committedVirtualMemorySize;
    }

    public void totalMemorySize(long totalMemorySize) {
        this.totalMemorySize = totalMemorySize;
    }

    public void freeMemorySize(long freeMemorySize) {
        this.freeMemorySize = freeMemorySize;
    }

    public void totalSwapSpaceSize(long totalSwapSpaceSize) {
        this.totalSwapSpaceSize = totalSwapSpaceSize;
    }

    public void processCpuLoad(double processCpuLoad2) {
        this.processCpuLoad = processCpuLoad2;
    }

    public void jvmFlags(List<JVMFlag> jvmFlags) {
        this.jvmFlags = jvmFlags;
    }

    public void gcType(GCType identifyGC) {
        this.gcType = identifyGC;
    }

    /**
     * @return the gcType
     */
    public GCType getGCType() {
     return gcType;
    }

    // Serialize this object to a JSONObject using org.json (avoids reflection)
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("id", id);
        putIfNotNull(o, "systemProperties", systemProperties);
        putIfNotNull(o, "environmentVariables", environmentVariables);
        o.put("processCpuLoad", processCpuLoad);
        o.put("freeSwapSpaceSize", freeSwapSpaceSize);
        o.put("cpuLoad", cpuLoad);
        o.put("processCpuTime", processCpuTime);
        putIfNotNull(o, "garbageCollectors", garbageCollectors);
        if (jvmFlags != null) {
            JSONArray flagsArr = new JSONArray();
            for (JVMFlag f : jvmFlags) {
                JSONObject fj = new JSONObject();
                fj.put("name", f.getName());
                fj.put("value", f.getValue());
                fj.put("origin", f.getOrigin());
                fj.put("writable", f.isWritable());
                flagsArr.put(fj);
            }
            o.put("jvmFlags", flagsArr);
        }
        putIfNotNull(o, "compilerName", compilerName);
        o.put("totalCompilationTime", totalCompilationTime);
        o.put("loadedClassCount", loadedClassCount);
        o.put("unloadedClassCount", unloadedClassCount);
        o.put("totalLoadedClassCount", totalLoadedClassCount);
        putIfNotNull(o, "inputArguments", inputArguments);
        putIfNotNull(o, "vmVendor", vmVendor);
        putIfNotNull(o, "vmVersion", vmVersion);
        putIfNotNull(o, "vmName", vmName);
        putIfNotNull(o, "pidHostname", pidHostname);
        if (heapMemoryUsage != null) o.put("heapMemoryUsage", memoryUsageToJson(heapMemoryUsage));
        if (nonHeapMemoryUsage != null) o.put("nonHeapMemoryUsage", memoryUsageToJson(nonHeapMemoryUsage));
        if (memoryPoolMXBeans != null) {
            JSONArray pools = new JSONArray();
            for (MemoryPoolMXBean b : memoryPoolMXBeans) {
                JSONObject pj = new JSONObject();
                pj.put("name", b.getName());
                pj.put("type", b.getType().toString());
                if (b.getUsage() != null) pj.put("usage", memoryUsageToJson(b.getUsage()));
                if (b.getPeakUsage() != null) pj.put("peakUsage", memoryUsageToJson(b.getPeakUsage()));
                pools.put(pj);
            }
            o.put("memoryPools", pools);
        }
        o.put("threadCount", threadCount);
        o.put("peakThreadCount", peakThreadCount);
        o.put("totalStartedThreadCount", totalStartedThreadCount);
        putIfNotNull(o, "osName", osName);
        putIfNotNull(o, "osVersion", osVersion);
        putIfNotNull(o, "osArch", osArch);
        o.put("systemLoadAverage", systemLoadAverage);
        o.put("freeMemorySize", freeMemorySize);
        o.put("totalMemorySize", totalMemorySize);
        o.put("committedVirtualMemory", committedVirtualMemory);
        o.put("totalSwapSpaceSize", totalSwapSpaceSize);
        o.put("availableProcessors", availableProcessors);
        if (gcType != null) o.put("gcType", gcType.name());
        return o;
    }

    private void putIfNotNull(JSONObject o, String key, Object value) {
        if (value == null) return;
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            o.put(key, new JSONArray(list.stream().map(Object::toString).collect(Collectors.toList())));
        } else {
            o.put(key, value);
        }
    }

    private JSONObject memoryUsageToJson(MemoryUsage mu) {
        JSONObject j = new JSONObject();
        j.put("init", mu.getInit());
        j.put("used", mu.getUsed());
        j.put("committed", mu.getCommitted());
        j.put("max", mu.getMax());
        return j;
    }

}