package org.example.threadsvisualiser;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * The Model class manages the connection to JVM processes and retrieves thread information.
 */
public class Model {

    private final ObservableList<Thread> observableThreadInfoList;

    private long selectedThreadId;

    private final SimpleStringProperty peakThreadCount = new SimpleStringProperty();
    private final SimpleStringProperty liveThreadCount = new SimpleStringProperty();
    private final SimpleStringProperty daemonThreadCount = new SimpleStringProperty();


    private String nameFilter;
    private String idFilter;
    private boolean nameFilterActive;
    private boolean idFilterActive;
    private boolean daemonFilterActive;

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledFuture;

    private final ThreadMXBean currentThreadMXBean = ManagementFactory.getThreadMXBean();


    public Model() {
        this.observableThreadInfoList = FXCollections.observableArrayList();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduledFuture = null;
    }

    // Get the root thread group
    public static ThreadGroup getRootThreadGroup() {
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;

        // Traverse up to the root thread group
        while ((parentGroup = threadGroup.getParent()) != null) {
            threadGroup = parentGroup;
        }

        return threadGroup;
    }

    public static Thread[] allThreads() {
        // Get the root thread group
        ThreadGroup rootGroup = getRootThreadGroup();

        // Estimate the number of active threads (double to ensure capacity)
        int estimatedSize = rootGroup.activeCount() * 2;

        Thread[] slackThreads = new Thread[estimatedSize];
        int actualSize = rootGroup.enumerate(slackThreads, true);

        // Copy the active threads into a new array with the correct size
        Thread[] threads = new Thread[actualSize];
        System.arraycopy(slackThreads, 0, threads, 0, actualSize);

        return threads;
    }



    // Stop a thread given its thread ID
    void interruptThread(){
        ThreadGroup threadGroup = getRootThreadGroup();
        int allActiveThreads = threadGroup.activeCount();
        Thread[] allThreads = new Thread[allActiveThreads];
        threadGroup.enumerate(allThreads);

        for(Thread thread : allThreads){
            if(thread.threadId() == selectedThreadId){
                thread.interrupt();
            }
        }
    }


    // Start a new thread
    void startNewThread(String threadName, String userInputCode, boolean isDaemon){

        try {
            // Extract the class name from user input
            String className = extractClassName(userInputCode);
            if (className == null) {
                System.err.println("Invalid class definition. Class name could not be found.");
                return;
            }

            // Compile the user-defined class
            File sourceFile = writeToFile(className, userInputCode);
            compileClass(sourceFile);

            // Load and execute the compiled class
            Class<?> clazz = loadClassFromFile(sourceFile.getParentFile(), className);
            Runnable runnableInstance = (Runnable) clazz.getDeclaredConstructor().newInstance();

            // Start the thread
            Thread thread = new Thread(runnableInstance);
            thread.setName(threadName);
            thread.setDaemon(isDaemon);
            thread.start();

            // Clean up the source and compiled files after starting the thread
            deleteGeneratedFiles(sourceFile, new File(sourceFile.getParent(), className + ".class"));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static String extractClassName(String code) {
        // Regular expression to find the class name after "class"
        Pattern pattern = Pattern.compile("class\\s+([A-Za-z_$][A-Za-z\\d_$]*)\\s+implements\\s+Runnable");
        Matcher matcher = pattern.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null; // Return null if no valid class name is found
    }

    private static File writeToFile(String className, String code) throws IOException {
        File compileDir = new File("./compiledClasses");
        if (!compileDir.exists()) compileDir.mkdirs(); // Create directory if it doesn't exist
        File sourceFile = new File(compileDir, className + ".java");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile))) {
            writer.write(code);
        }

        return sourceFile;
    }

    private static void compileClass(File sourceFile) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.getTask(null, null, null,
                        List.of("-d", sourceFile.getParent()), null,
                        compiler.getStandardFileManager(null, null, null)
                                .getJavaFileObjectsFromFiles(List.of(sourceFile)))
                .call();
    }

    private static Class<?> loadClassFromFile(File compileDir, String className) throws Exception {
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{compileDir.toURI().toURL()});
        return Class.forName(className, true, classLoader);
    }

    private static void deleteGeneratedFiles(File... files) {
        for (File file : files) {
            if (file.exists()) {
                file.delete();
            }
        }
    }


    /**
     * Refreshes the thread data.
     */
    public void refreshData() {

        Thread[] threads = allThreads();

        List<Thread> tempThreadInfo = Arrays.stream(threads)
                .parallel()
                .filter(threadInfo -> !nameFilterActive || threadInfo.getName().contains(nameFilter))
                .filter(threadInfo -> !idFilterActive || String.valueOf(threadInfo.getId()).contains(idFilter))
                .filter(threadInfo -> !daemonFilterActive || threadInfo.isDaemon())
                .collect(Collectors.toList());


        Platform.runLater(() -> {
            peakThreadCount.set(String.valueOf(currentThreadMXBean.getPeakThreadCount()));
            liveThreadCount.set(String.valueOf(currentThreadMXBean.getThreadCount()));
            daemonThreadCount.set(String.valueOf(currentThreadMXBean.getDaemonThreadCount()));
            observableThreadInfoList.setAll(tempThreadInfo);
        });
    }

    /**
     * Starts the scheduler to refresh thread data at the specified refresh rate.
     *
     * @param refreshRate the refresh rate in milliseconds
     */
    public void startRefreshDataScheduler(int refreshRate) {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = scheduler.scheduleAtFixedRate(this::refreshData, 0, refreshRate, TimeUnit.MILLISECONDS);
    }


    public String getDetailedThreadInfo(Thread thread) {

        ThreadInfo threadInfo = currentThreadMXBean.getThreadInfo(thread.threadId());
        StringBuilder output = new StringBuilder();
        output.append("Thread ID: ").append(threadInfo.getThreadId()).append("\n");
        output.append("Thread Name: ").append(threadInfo.getThreadName()).append("\n");
        output.append("Thread State: ").append(threadInfo.getThreadState()).append("\n");
        output.append("Lock Name: ").append(threadInfo.getLockName()).append("\n");
        output.append("Lock Owner Name: ").append(threadInfo.getLockOwnerName()).append("\n");
        output.append("Lock Owner ID: ").append(threadInfo.getLockOwnerId()).append("\n");
        output.append("Blocked Time: ").append(threadInfo.getBlockedTime()).append("\n");
        output.append("Blocked Count: ").append(threadInfo.getBlockedCount()).append("\n");
        output.append("Waited Time: ").append(threadInfo.getWaitedTime()).append("\n");
        output.append("Waited Count: ").append(threadInfo.getWaitedCount()).append("\n");
        output.append("In Native: ").append(threadInfo.isInNative()).append("\n");
        output.append("Suspended: ").append(threadInfo.isSuspended()).append("\n");
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        if (stackTrace != null && stackTrace.length > 1) {
            output.append("Stack Trace: \n");
            for (int i = 1; i < stackTrace.length; i++)
                output.append("\tat ").append(stackTrace[i]).append("\n");
        }
        return output.toString();
    }

    /**
     * Retrieves information about deadlocked threads.
     *
     * @return a string containing information about deadlocked threads
     */
    public String getDeadlockedThreads() {
        StringBuilder output = new StringBuilder();
        long[] deadlockedThreads = currentThreadMXBean.findDeadlockedThreads();
        long[] monitorDeadlockedThreads = currentThreadMXBean.findMonitorDeadlockedThreads();

        if (deadlockedThreads == null && monitorDeadlockedThreads == null) {
            output.append("No deadlocked threads found.");
            return output.toString();
        }

        if (deadlockedThreads != null) {
            output.append("Deadlocked threads: \n");
            output.append("---------------------------\n");

            for (long threadId : deadlockedThreads) {
                output.append("Thread ID: ").append(threadId).append("\n");
                output.append("Thread Name: ").append(currentThreadMXBean.getThreadInfo(threadId).getThreadName()).append("\n");
                output.append("Waiting on: ").append(currentThreadMXBean.getThreadInfo(threadId).getLockName()).append("\n");
                output.append("Locked by: ").append(currentThreadMXBean.getThreadInfo(threadId).getLockOwnerName()).append("\n");
            }
        }

        if (monitorDeadlockedThreads != null) {
            output.append("Monitor deadlocked threads: \n");
            output.append("---------------------------\n");

            for (long threadId : monitorDeadlockedThreads) {
                output.append("Thread ID: ").append(threadId).append("\n");
                output.append("Thread Name: ").append(currentThreadMXBean.getThreadInfo(threadId).getThreadName()).append("\n");
                output.append("Waiting on: ").append(currentThreadMXBean.getThreadInfo(threadId).getLockName()).append("\n");
                output.append("Locked by: ").append(currentThreadMXBean.getThreadInfo(threadId).getLockOwnerName()).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdownScheduler() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduler.shutdown();
    }

    /**
     * Sets the name filter for thread filtering.
     *
     * @param newValue the new name filter value
     */
    public void setNameFilter(String newValue) {
        this.nameFilter = newValue;
        this.nameFilterActive = !newValue.isEmpty();
    }

    /**
     * Sets the ID filter for thread filtering.
     *
     * @param newValue the new ID filter value
     */
    public void setIdFilter(String newValue) {
        this.idFilter = newValue;
        this.idFilterActive = !newValue.isEmpty();
    }

    /**
     * Sets the daemon filter for thread filtering.
     *
     * @param val the new daemon filter value
     */
    public void setDaemonFilter(boolean val) {
        this.daemonFilterActive = val;
    }


    /**
     * Retrieves the observable list of ThreadInfo objects.
     *
     * @return the observable list of ThreadInfo objects
     */
    public ObservableList<Thread> getObservableThreadInfoList() {
        return observableThreadInfoList;
    }

    /**
     * Retrieves the peak thread count property.
     *
     * @return the peak thread count property
     */
    public SimpleStringProperty getPeakThreadCount() {
        return peakThreadCount;
    }

    /**
     * Retrieves the thread count property.
     *
     * @return the thread count property
     */
    public SimpleStringProperty getLiveThreadCount() {
        return liveThreadCount;
    }

    /**
     * Retrieves the daemon thread count property.
     *
     * @return the daemon thread count property
     */
    public SimpleStringProperty getDaemonThreadCount() {
        return daemonThreadCount;
    }

    /**
     * Checks if the name filter is active.
     *
     * @return true if the name filter is active, false otherwise
     */
    public boolean isNameFilterActive() {
        return nameFilterActive;
    }

    /**
     * Checks if the ID filter is active.
     *
     * @return true if the ID filter is active, false otherwise
     */
    public boolean isIdFilterActive() {
        return idFilterActive;
    }

    /**
     * Checks if the daemon filter is active.
     *
     * @return true if the daemon filter is active, false otherwise
     */
    public boolean isDaemonFilterActive() {
        return daemonFilterActive;
    }

    public long getSelectedThreadId() {
        return selectedThreadId;
    }

    public void setSelectedThreadId(long selectedThreadId) {
        this.selectedThreadId = selectedThreadId;
    }

}
