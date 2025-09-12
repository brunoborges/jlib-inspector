package io.github.brunoborges.jlib.agent.jvm;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ShowJVM {

    public static void main(String[] args) {
        System.out.println(new ShowJVM().dumpJVMDetails());
    }

    private StringBuilder buffer = new StringBuilder(5000);

    private void append(Object s) {
        buffer.append(s).append("\n");
    }

    private void append(String msg, Object... s) {
        buffer.append(String.format(msg, s)).append("\n");
    }

    private String bytesToMBString(long bytes) {
        return bytes / 1024 / 1024 + " MB";
    }

    public JVMDetails extractJVMDetails() {
        var jvmDetails = new JVMDetails();
        jvmDetails.pidHostname(ManagementFactory.getRuntimeMXBean().getName());

        // Runtime MBean
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        jvmDetails.vmName(runtimeMXBean.getVmName());
        jvmDetails.vmVersion(runtimeMXBean.getVmVersion());
        jvmDetails.vmVendor(runtimeMXBean.getVmVendor());
        jvmDetails.inputArguments(runtimeMXBean.getInputArguments());

        // Class Loading MBean
        var classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
        jvmDetails.totalLoadedClassCount(classLoadingMXBean.getTotalLoadedClassCount());
        jvmDetails.unloadedClassCount(classLoadingMXBean.getUnloadedClassCount());
        jvmDetails.loadedClassCount(classLoadingMXBean.getLoadedClassCount());

        // Compilation MBean
        var compilationMXBean = ManagementFactory.getCompilationMXBean();
        jvmDetails.compilerName(compilationMXBean.getName());
        jvmDetails.totalCompilationTime(compilationMXBean.getTotalCompilationTime());

        // Memory MBean
        var memoryMXBean = ManagementFactory.getMemoryMXBean();
        jvmDetails.heapMemoryUsage(memoryMXBean.getHeapMemoryUsage());
        jvmDetails.nonHeapMemoryUsage(memoryMXBean.getNonHeapMemoryUsage());

        // Memory Pool MBean
        var memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        jvmDetails.memoryPoolMXBeans(memoryPoolMXBeans);

        // Thread MBean
        var threadMXBean = ManagementFactory.getThreadMXBean();
        jvmDetails.threadCount(threadMXBean.getThreadCount());
        jvmDetails.peakThreadCount(threadMXBean.getPeakThreadCount());
        jvmDetails.totalStartedThreadCount(threadMXBean.getTotalStartedThreadCount());

        // Operating System MBean
        var operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        jvmDetails.osName(operatingSystemMXBean.getName());
        jvmDetails.osVersion(operatingSystemMXBean.getVersion());
        jvmDetails.osArch(operatingSystemMXBean.getArch());
        jvmDetails.availableProcessors(operatingSystemMXBean.getAvailableProcessors());
        jvmDetails.systemLoadAverage(operatingSystemMXBean.getSystemLoadAverage());

        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean _osBean) {
            jvmDetails.committedVirtualMemory(_osBean.getCommittedVirtualMemorySize());
            jvmDetails.totalMemorySize(_osBean.getTotalMemorySize());
            jvmDetails.freeMemorySize(_osBean.getFreeMemorySize());
            jvmDetails.totalSwapSpaceSize(_osBean.getTotalSwapSpaceSize());
            jvmDetails.freeSwapSpaceSize(_osBean.getFreeSwapSpaceSize());
            jvmDetails.cpuLoad(_osBean.getCpuLoad());
            jvmDetails.processCpuLoad(_osBean.getProcessCpuLoad());
            jvmDetails.processCpuTime(_osBean.getProcessCpuTime());
        }

        // PrintFlagsFinal
        PrintFlagsFinal printFlagsFinal = new PrintFlagsFinal();

        // JVM Flags Final
        jvmDetails.jvmFlags(printFlagsFinal.getJVMFlags());

        // Garbage Collector MBean (reuse printFlagsFinal if possible)
        var identifyGC = new IdentifyGC(printFlagsFinal);
        jvmDetails.gcType(identifyGC.getGCType());

        var garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        jvmDetails.garbageCollectors(garbageCollectorMXBeans.stream()
                .map(gcBean -> gcBean.getName() + ": " + gcBean.getObjectName().toString())
                .collect(Collectors.toList()));

        // Environment Variables
        jvmDetails.environmentVariables(System.getenv().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList()));

        // System Properties
        jvmDetails.systemProperties(System.getProperties().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList()));

        return jvmDetails;
    }

    public String dumpJVMDetails() {
        runtimeProperties();
        memorySettings();
        loadedClasses();
        compilationStats();
        garbageCollectorInfo();
        cpuUsage();
        threadDetails();
        systemProperties();
        environmentVariables();
        jvmFlags();

        String _return = buffer.toString();
        buffer.setLength(0);
        return _return;
    }

    private void jvmFlags() {
        // JVM Final Flags
        append("");
        append("## JVM Final Flags:");
        new PrintFlagsFinal().getJVMFlags().forEach(this::append);
    }

    private void environmentVariables() {
        // Environment Variables
        append("");
        append("## Environment variables:");
        System.getenv().forEach(this::appendJVMProperty);
    }

    private void systemProperties() {
        // System Properties
        append("");
        append("## All System Properties:");
        System.getProperties().forEach(this::appendJVMProperty);
    }

    private void threadDetails() {
        // Threads
        var threads = ManagementFactory.getThreadMXBean();
        append("");
        append("## Threads");

        append("Threads / Started Threads / Peak: " + threads.getThreadCount() + " / "
                + threads.getTotalStartedThreadCount() + " / " + threads.getPeakThreadCount());
        append("CPU Time / User Time: " + threads.getCurrentThreadCpuTime() + " / "
                + threads.getCurrentThreadUserTime());

        Arrays.stream(threads.dumpAllThreads(true, true))
                .forEach(thread -> append("Thread Name: " + thread.getThreadName()));
    }

    private void cpuUsage() {
        // CPU Usage
        append("");
        append("## CPU");

        var osBean = ManagementFactory.getOperatingSystemMXBean();
        append("osMXBean.getSystemLoadAverage: %s", Double.toString(osBean.getSystemLoadAverage()));
        append("osMXBean.getAvailableProcessors: %s", Integer.toString(osBean.getAvailableProcessors()));

        var _osBean = (com.sun.management.OperatingSystemMXBean) osBean;
        append("osMXBean.getCommittedVirtualMemorySize: %s", bytesToMBString(_osBean.getCommittedVirtualMemorySize()));
        append("osMXBean.getTotalPhysicalMemorySize: %s", bytesToMBString(_osBean.getTotalMemorySize()));
        append("osMXBean.getFreePhysicalMemorySize: %s", bytesToMBString(_osBean.getFreeMemorySize()));
        append("osMXBean.getTotalSwapSpaceSize: %s", bytesToMBString(_osBean.getTotalSwapSpaceSize()));
        append("osMXBean.getFreeSwapSpaceSize: %s", bytesToMBString(_osBean.getFreeSwapSpaceSize()));
        append("osMXBean.getSystemCpuLoad: %s", Double.toString(_osBean.getCpuLoad()));
        append("osMXBean.getProcessCpuLoad: %s", Double.toString(_osBean.getProcessCpuLoad()));
        append("osMXBean.getProcessCpuTime: %s", Double.toString(_osBean.getProcessCpuTime()));
    }

    private void garbageCollectorInfo() {
        // Garbage Collector
        append("");
        append("## Garbage Collectors");
        append(" - GC Type: " + new IdentifyGC().getGCType());
        var gcMxBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (var gcBean : gcMxBeans) {
            append(gcBean.getName() + ": " + gcBean.getObjectName().toString());
        }
    }

    private void compilationStats() {
        // Compilation
        var compiler = ManagementFactory.getCompilationMXBean();
        append("");
        append("## Compiler");
        append("Compiler Name: " + compiler.getName());
        append("Total Compilation Time: " + compiler.getTotalCompilationTime());
    }

    private void loadedClasses() {
        // Loaded Classes
        var classLoading = ManagementFactory.getClassLoadingMXBean();
        append("");
        append("## Loaded Classes");
        append("Total # of loaded classes (from the RuntimeInfo start): " + classLoading.getTotalLoadedClassCount());
        append("Total # of unloaded classes: " + classLoading.getUnloadedClassCount());
        append("Current # of loaded classes: " + classLoading.getLoadedClassCount());
    }

    private void memorySettings() {
        // Memory Settings
        final int mb = 1024 * 1024;
        Runtime runtime = Runtime.getRuntime();
        append("");
        append("## Memory Settings [MB]");
        append("Used Memory: " + (runtime.totalMemory() - runtime.freeMemory()) / mb + "MB");
        append("Free Memory: " + runtime.freeMemory() / mb + "MB");
        append("Total Memory: " + runtime.totalMemory() / mb + "MB");
        append("Max Memory: " + runtime.maxMemory() / mb + "MB");
    }

    private void runtimeProperties() {
        // Runtime Properties
        append("");
        append("## Runtime Properties");
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        append(runtimeMXBean.getVmName() + " " + runtimeMXBean.getVmVersion());
        append("PID @ Hostname: " + runtimeMXBean.getName());
        runtimeMXBean.getInputArguments().forEach(arg -> append("RuntimeMXBean input: " + arg));
    }

    private final StringBuilder sb_reuse = new StringBuilder();

    private void appendJVMProperty(Object key, Object value) {
        String v = value == null ? "" : value.toString();
        String line = sb_reuse.append("\"").append(key).append("\": \"").append(v.replace("\\", "\\\\")).append("\"")
                .toString();
        append(line);
        sb_reuse.setLength(0);
    }

}