package io.github.brunoborges.jlib.agent.jvm;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.logging.Logger;

public class IdentifyGC {

    public static enum GCType {
        G1GC, ConcMarkSweepGC, ParallelGC, SerialGC, ShenandoahGC, ZGC, Unknown;
    }

    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    private static final Logger LOGGER = Logger.getLogger(IdentifyGC.class.getName());

    private final GCType identifiedGC;

    private Class<?> vmOptionClazz, hotSpotDiagnosticMXBeanClazz;

    private PrintFlagsFinal flags;

    public IdentifyGC() {
        this(null);
    }

    public IdentifyGC(PrintFlagsFinal flags) {
        try {
            vmOptionClazz = Class.forName("com.sun.management.VMOption");
            hotSpotDiagnosticMXBeanClazz = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
        } catch (ClassNotFoundException e) {
            LOGGER.warning("Can't read VMOption nor HotSpotDiagnosticMXBean classes.");
        }

        this.flags = flags;

        identifiedGC = identifyGC();
    }

    private GCType identifyGC() {
        try {
            var flags = Arrays.asList(GCType.values());
            var flagSettings = new TreeMap<GCType, String>();
            for (var flag : flags) {
                var vmOption = getVMOption("Use" + flag.name());
                if (vmOption != null) {
                    flagSettings.put(flag, vmOption);
                }
            }
            return flagSettings.entrySet().stream().filter(e -> "true".equals(e.getValue())).map(e -> e.getKey())
                    .findFirst().orElse(GCType.Unknown);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getVMOption(String vmOptionName) {
        if (flags != null) {
            String vmOption = flags.getVMOption(vmOptionName);
            if (vmOption != null) {
                return vmOption;
            } else {
                // if we don't have one GC flag, we don't have any of the GC flags
                flags = null;
            }
        }

        // initialize hotspot diagnostic MBean
        initHotspotMBean();
        try {
            var publicLookup = MethodHandles.publicLookup();
            var mt = MethodType.methodType(vmOptionClazz, String.class);
            var getVMOption = publicLookup.findVirtual(hotSpotDiagnosticMXBeanClazz, "getVMOption", mt);
            var vmOption = getVMOption.invokeWithArguments(hotspotMBean, vmOptionName);

            var mt2 = MethodType.methodType(String.class);
            var mh2 = publicLookup.findVirtual(vmOptionClazz, "getValue", mt2);
            return (String) mh2.invokeWithArguments(vmOption);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("does not exist")) {
                return null;
            }
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private Object hotspotMBean;

    private void initHotspotMBean() {
        if (hotspotMBean == null) {
            synchronized (IdentifyGC.class) {
                if (hotspotMBean == null) {
                    hotspotMBean = getHotspotMBean();
                }
            }
        }
    }

    private Object getHotspotMBean() {
        try {
            var server = ManagementFactory.getPlatformMBeanServer();
            return ManagementFactory.newPlatformMXBeanProxy(server, HOTSPOT_BEAN_NAME, hotSpotDiagnosticMXBeanClazz);
        } catch (Exception re) {
            LOGGER.warning("Can't proxy HotSpotDiagnosticMXBean.");
        }

        return null;
    }

    public GCType getGCType() {
        return identifiedGC;
    }

}