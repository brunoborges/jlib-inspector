package io.github.brunoborges.jlib.agent.jvm;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;

public class PrintFlagsFinal {

    private final static Logger LOGGER = Logger.getLogger(PrintFlagsFinal.class.getName());

    private boolean fallbackToHotSpotDiagnosticMXBean = false;

    private Method getAllFlagsMethod = null;

    private HotSpotDiagnosticMXBean hotspotDiagBean;

    private List<JVMFlag> jvmFlags;

    public PrintFlagsFinal() {
        hotspotDiagBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        try {
            final Class<?> flagClass = Class.forName("com.sun.management.internal.Flag");
            getAllFlagsMethod = flagClass.getDeclaredMethod("getAllFlags");
            getAllFlagsMethod.setAccessible(true);
        } catch (Exception e) {
            Class<?> inaccessibleException = null;
            try {
                inaccessibleException = Class.forName("java.lang.reflect.InaccessibleObjectException");
            } catch (ClassNotFoundException e1) {
            }

            if (inaccessibleException != null && e.getClass().equals(inaccessibleException)) {
                LOGGER.log(Level.SEVERE, "This JVM does not open package jdk.management/com.sun.management.internal to this module. To include all flags, run with --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED");
            }

            fallbackToHotSpotDiagnosticMXBean = true;
        }

        readJVMFlags();
    }

    public List<JVMFlag> getJVMFlags() {
        return jvmFlags;
    }

    protected void readJVMFlags() {
        // Stop if this has already been done
        if (jvmFlags == Collections.EMPTY_LIST || jvmFlags != null)
            return;

        List<VMOption> options = Collections.emptyList();

        if (fallbackToHotSpotDiagnosticMXBean && hotspotDiagBean == null) {
            LOGGER.log(Level.SEVERE, "This JVM does not support HotSpotDiagnosticMXBean. Cannot get any flags.");
        } else if (fallbackToHotSpotDiagnosticMXBean) {
            // only includes writable external flags
            options = hotspotDiagBean.getDiagnosticOptions();
        } else {
            options = getAllFlagsFromInternal();
        }

        Map<String, VMOption> optionMap = new TreeMap<>();
        for (final VMOption option : options) {
            optionMap.put(option.getName(), option);
        }

        List<JVMFlag> flagsFound = new ArrayList<>(optionMap.size());

        for (VMOption option : optionMap.values()) {
            LOGGER.info(option.getName() + " = " + option.getValue() + " (" + option.getOrigin() + ", "
                    + (option.isWriteable() ? "read-write" : "read-only") + ")");

            var jvmFlag = new JVMFlag(option.getName(), option.getValue(), option.getOrigin().name(),
                    option.isWriteable());
            flagsFound.add(jvmFlag);
        }
        LOGGER.info(options.size() + " options found");
        jvmFlags = flagsFound;
    }

    private List<VMOption> getAllFlagsFromInternal() {
        List<VMOption> options = Collections.emptyList();
        try {
            final Class<?> flagClass = Class.forName("com.sun.management.internal.Flag");
            final Method getAllFlagsMethod = flagClass.getDeclaredMethod("getAllFlags");
            final Method getVMOptionMethod = flagClass.getDeclaredMethod("getVMOption");
            getAllFlagsMethod.setAccessible(true);
            getVMOptionMethod.setAccessible(true);
            final Object result = getAllFlagsMethod.invoke(null);
            final List<?> flags = (List<?>) result;
            options = new ArrayList<VMOption>(flags.size());
            for (final Object flag : flags) {
                options.add((VMOption) getVMOptionMethod.invoke(flag));
            }
        } catch (Exception e) {

            Class<?> inaccessibleException = null;
            try {
                inaccessibleException = Class.forName("java.lang.reflect.InaccessibleObjectException");
            } catch (ClassNotFoundException e1) {
            }

            if (inaccessibleException != null && e.getClass().equals(inaccessibleException)) {
                LOGGER.log(Level.SEVERE, "This JVM does not open package jdk.management/com.sun.management.internal to this module. To include all flags, run with --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED");
            }
        }
        return options;
    }

    public static class JVMFlag {
        private String name;
        private String value;
        private String origin;
        private boolean writable;

        private final String _toString;

        public JVMFlag(String name, String value, String origin, boolean writable) {
            this.name = name;
            this.value = value;
            this.origin = origin;
            this.writable = writable;

            _toString = String.format("%s = %s (%s, %s)", name, value, origin, writable ? "read-write" : "read-only");
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getOrigin() {
            return origin;
        }

        public boolean isWritable() {
            return writable;
        }

        @Override
        public String toString() {
            return _toString;
        }
    }

    public String getVMOption(String vmOptionName) {
        return jvmFlags.stream().filter(flag -> flag.getName().equals(vmOptionName)).findFirst().map(JVMFlag::getValue)
                .orElse(null);
    }

}