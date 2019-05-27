package com.acelost.spectrum;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.util.*;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

/**
 * Utility for UI state monitoring. Prints current application state in logcat.
 *
 * To start monitoring concrete activity use {@link #explore(Activity)}.
 * To start monitoring all activities use {@link #explore(Application)}.
 * To start building a report manually call {@link #report()}.
 * To configure output call {@link #configure()} and setup Spectrum in builder style.
 */
public class Spectrum {

    /**
     * Spectrum configuration delegate.
     */
    public static class Configuration {

        private static String LOG_TAG = "Spectrum";
        private static int LOG_LEVEL = Log.DEBUG;
        private static boolean APPEND_PACKAGES = false;
        private static boolean APPEND_VIEW_ID = true;
        private static boolean APPEND_VIEW_LOCATION = false;
        private static boolean SHOW_VIEW_HIERARCHY = true;
        private static boolean AUTO_REPORTING = true;
        private static boolean SAMPLE_REPORTING = true;
        private static int SAMPLE_REPORTING_MS = 500;

        /**
         * Set log tag you want to use for output.
         */
        @NonNull
        public Configuration logTag(@NonNull String tag) {
            Configuration.LOG_TAG = tag;
            return this;
        }

        /**
         * Set log level you want to use for output (see valid values in {@link Log} class).
         */
        @NonNull
        public Configuration logLevel(int level) {
            Configuration.LOG_LEVEL = level;
            return this;
        }

        /**
         * Whether to append packages to class name or not.
         */
        @NonNull
        public Configuration appendPackages(boolean append) {
            Configuration.APPEND_PACKAGES = append;
            return this;
        }

        /**
         * Whether to append view id to {@link View} nodes.
         */
        @NonNull
        public Configuration appendViewId(boolean append) {
            Configuration.APPEND_VIEW_ID = append;
            return this;
        }

        /**
         * Whether to append view location to {@link View} nodes.
         */
        @NonNull
        public Configuration appendViewLocation(boolean append) {
            Configuration.APPEND_VIEW_LOCATION = append;
            return this;
        }

        /**
         * Whether to display view hierarchy.
         */
        @NonNull
        public Configuration showViewHierarchy(boolean show) {
            Configuration.SHOW_VIEW_HIERARCHY = show;
            return this;
        }

        /**
         * Whether to trigger building report automatically after any changes.
         */
        @NonNull
        public Configuration autoReporting(boolean enable) {
            Configuration.AUTO_REPORTING = enable;
            return this;
        }

        /**
         * Whether to sample reporting or build new report after any changes.
         */
        @NonNull
        public Configuration sampleReporting(boolean sample) {
            Configuration.SAMPLE_REPORTING = sample;
            return this;
        }

        private static void parseConfigFromResources(@NonNull Context context) {
            int id;
            if ((id = getStringResId(context, "spectrum_log_tag")) != 0) {
                Configuration.LOG_TAG = context.getString(id);
            }
            if ((id = getIntResId(context, "spectrum_log_level")) != 0) {
                Configuration.LOG_LEVEL = context.getResources().getInteger(id);
            }
            if ((id = getBoolResId(context, "spectrum_append_packages")) != 0) {
                Configuration.APPEND_PACKAGES = context.getResources().getBoolean(id);
            }
            if ((id = getBoolResId(context, "spectrum_append_view_id")) != 0) {
                Configuration.APPEND_VIEW_ID = context.getResources().getBoolean(id);
            }
            if ((id = getBoolResId(context, "spectrum_append_view_location")) != 0) {
                Configuration.APPEND_VIEW_LOCATION = context.getResources().getBoolean(id);
            }
            if ((id = getBoolResId(context, "spectrum_show_view_hierarchy")) != 0) {
                Configuration.SHOW_VIEW_HIERARCHY = context.getResources().getBoolean(id);
            }
            if ((id = getBoolResId(context, "spectrum_auto_reporting")) != 0) {
                Configuration.AUTO_REPORTING = context.getResources().getBoolean(id);
            }
            if ((id = getBoolResId(context, "spectrum_sample_reporting")) != 0) {
                Configuration.SAMPLE_REPORTING = context.getResources().getBoolean(id);
            }
        }

        private static int getStringResId(@NonNull Context context, @NonNull String name) {
            return getResId(context, name, "string");
        }

        private static int getIntResId(@NonNull Context context, @NonNull String name) {
            return getResId(context, name, "integer");
        }

        private static int getBoolResId(@NonNull Context context, @NonNull String name) {
            return getResId(context, name, "bool");
        }

        private static int getResId(@NonNull Context context, @NonNull String name, @NonNull String defType) {
            return context.getResources().getIdentifier(name, defType, context.getPackageName());
        }
    }

    private static final String OUTPUT_HORIZONTAL_DIVIDER =     "――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――――\n";
    private static final String TITLE_SPECTRUM_STATE_REPORT =   "                                          SPECTRUM REPORT                                           \n";
    private static final String HEADER_HIERARCHY = "HIERARCHY:\n";
    private static final String HEADER_CHANGES = "CHANGES:\n";

    private static final int LOGCAT_BUFFER_SIZE = 4000;

    private static boolean initialized = false;

    private static WeakReference<Application> applicationRef;

    private static WeakHashMap<Activity, Long> activities;

    private static ApplicationObserver applicationObserver;

    private static List<ActivityObserver> activityObservers;

    private static Handler handler;

    private static List<String> pendingChanges;

    private static Runnable reportRunnable;

    private static long scheduledReportTime = 0;

    /**
     * Start monitoring of application.
     */
    public static void explore(@NonNull Application application) {
        if (!prepare(application)) return;
        final Application observing = applicationRef != null ? applicationRef.get() : null;
        if (observing != application) {
            if (observing != null) {
                observing.unregisterActivityLifecycleCallbacks(applicationObserver);
            }
            if (applicationObserver == null) {
                applicationObserver = new ApplicationObserver();
            }
            applicationRef = new WeakReference<>(application);
            application.registerActivityLifecycleCallbacks(applicationObserver);
        }
    }

    /**
     * Start monitoring of activity.
     */
    public static void explore(@NonNull Activity activity) {
        if (!prepare(activity)) return;
        if (activities.containsKey(activity)) return;

        activities.put(activity, null);

        if (activity instanceof LifecycleOwner) {
            final LifecycleOwner lifecycleOwner = (LifecycleOwner) activity;
            final LifecycleObserver activityObserver = new ActivityObserver(activity);
            lifecycleOwner.getLifecycle().addObserver(activityObserver);
        } else {
            Log.e(Configuration.LOG_TAG,
                    "Activity " + activity.getClass().getName() +
                            "can't be explored cause it should implement LifecycleOwner interface.");
        }
        if (activity instanceof FragmentActivity) {
            final FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
            fragmentManager.registerFragmentLifecycleCallbacks(new FragmentObserver(), true);
        }
    }

    /**
     * Configure state output.
     */
    @NonNull
    public static Configuration configure() {
        return new Configuration();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean prepare(@NonNull Context context) {
        if (!BuildConfig.DEBUG) {
            // Disable spectrum for release builds
            return false;
        }
        if (!initialized) {
            synchronized (Spectrum.class) {
                if (!initialized) {
                    activities = new WeakHashMap<>();
                    activityObservers = new ArrayList<>();
                    handler = new Handler(Looper.getMainLooper());
                    pendingChanges = new ArrayList<>();
                    reportRunnable = new Runnable() {
                        @Override
                        public void run() {
                            report();
                        }
                    };
                    Configuration.parseConfigFromResources(context);
                    initialized = true;
                }
            }
        }
        return true;
    }

    // region Schedule Reporting

    /**
     * Trigger building a report manually.
     */
    public static void report() {
        if (!initialized) return;
        if (!isMainThread()) {
            // Redirect reporting to main thread
            scheduleReporting(0);
            return;
        }
        final long startBuildTime = System.nanoTime();
        final ApplicationStateTree tree = buildAppStateTree();
        final List<String> output = buildReport(tree, System.nanoTime() - startBuildTime);
        print(output);
        recycleAppStateTree(tree);
    }

    private static void scheduleReporting(long delay) {
        handler.removeCallbacks(reportRunnable);
        if (delay == 0) {
            scheduledReportTime = 0;
            handler.post(reportRunnable);
        } else {
            scheduledReportTime = System.currentTimeMillis() + delay;
            handler.postDelayed(reportRunnable, delay);
        }
    }

    private static void notifyChangesDetected(@NonNull String changeDescription) {
        if (pendingChanges.isEmpty() || !(pendingChanges.get(pendingChanges.size() - 1).equals(changeDescription))) {
            // Append change to pending only if it distinct from last
            pendingChanges.add(changeDescription);
        }
        // Schedule building report if auto reporting enabled
        if (Configuration.AUTO_REPORTING) {
            if (Configuration.SAMPLE_REPORTING) {
                final long now = System.currentTimeMillis();
                if (scheduledReportTime < now) {
                    // There shouldn't be scheduled reporting, schedule it right now
                    scheduleReporting(Configuration.SAMPLE_REPORTING_MS);
                }
            } else {
                report();
            }
        }
    }

    // endregion

    // region Reporting

    private static void print(@NonNull List<String> messages) {
        for (String message : messages) {
            Log.println(Configuration.LOG_LEVEL, Configuration.LOG_TAG, message);
        }
    }

    @NonNull
    private static List<String> buildReport(@NonNull ApplicationStateTree tree, long buildTimeNs) {
        final OutputBuilder output = new OutputBuilder()
                .append(String.format(Locale.getDefault(), "Report built in %.1f ms\n", buildTimeNs / 1000000f))
                .append(OUTPUT_HORIZONTAL_DIVIDER)
                .append(TITLE_SPECTRUM_STATE_REPORT)
                .append(HEADER_HIERARCHY);

        for (ActivityNode activityNode : tree.activities) {
            visitActivity(activityNode, output);
        }

        if (pendingChanges.size() > 0) {
            output.newline().append(HEADER_CHANGES);
            for (String change : pendingChanges) {
                output.append(" - ").append(change).newline();
            }
            pendingChanges.clear();
        }

        output.append(OUTPUT_HORIZONTAL_DIVIDER);
        return output.build();
    }

    private static void visitActivity(@NonNull ActivityNode node, @NonNull OutputBuilder output) {
        final int level = 0;
        final Activity activity = node.activity;

        indent(output, level)
                .append("⬟[Activity] ")
                .append(formatClassLink(activity))
                .append(" [").append(node.state).append("]")
                .newline();

        for (FragmentNode fragmentNode : node.fragment) {
            visitFragment(fragmentNode, level + 2, output);
        }

        for (ViewNode viewNode : node.views) {
            visitView(viewNode, level + 2, output);
        }
    }

    private static void visitView(@NonNull ViewNode node, int level, @NonNull OutputBuilder output) {
        final View view = node.view;
        final int visibility = view.getVisibility();
        indent(output, level)
                .append(view instanceof ViewGroup
                        ? visibility == View.VISIBLE ? "▸[ViewGroup] " : "▹[ViewGroup]"
                        : visibility == View.VISIBLE ? "●[View] " : "○[View] ")
                .append(formatClassLink(view));

        if (Configuration.APPEND_VIEW_ID) {
            final int id = view.getId();
            if (id != View.NO_ID && !isViewIdGenerated(id)) {
                try {
                    final String idName = view.getResources().getResourceEntryName(id);
                    if (idName != null) {
                        output.append(" [id/").append(idName).append("]");
                    }
                } catch (Resources.NotFoundException e) {
                    Log.w(Configuration.LOG_TAG, "Failed to obtain view id name. Possibly id was manually generated.");
                }
            }
        }

        if (Configuration.APPEND_VIEW_LOCATION) {
            if (view.getParent() != null) {
                if (visibility == View.GONE) {
                    output.append(" [gone]");
                } else {
                    final Rect location = new Rect();
                    view.getGlobalVisibleRect(location);
                    output.append(" ").append(formatLocation(location));
                }
            } else {
                output.append(" [out of layout]");
            }
        }

        output.newline();

        for (FragmentNode fragmentNode : node.fragments) {
            visitFragment(fragmentNode, level + 1, output);
        }

        for (ViewNode viewNode : node.child) {
            visitView(viewNode, level + 2, output);
        }
    }

    private static void visitFragment(@NonNull FragmentNode node, int level, @NonNull OutputBuilder output) {
        indent(output, level)
                .append(isDialogFragment(node.fragment)
                        ? "◇[DialogFragment] "
                        : isAttachedToLayout(node.fragment)
                                ? "■[Fragment] "
                                : "□[Fragment(out-of-layout)] ")
                .append(formatClassLink(node.fragment));
        if (node.fragment.getTag() != null) {
            output.append(" [tag \'").append(node.fragment.getTag()).append("\']");
        }
        output.newline();

        // If fragment inside view hierarchy, increase indent by 1 otherwise by 2
        final int nextLevel = node.view != null ? level + 1 : level + 2;

        for (FragmentNode fragmentNode : node.child) {
            visitFragment(fragmentNode, nextLevel, output);
        }

        if (node.view != null) {
            visitView(node.view, nextLevel, output);
        }
    }

    // endregion

    // region ApplicationStateTree building

    @NonNull
    private static ApplicationStateTree buildAppStateTree() {
        final ApplicationStateTree tree = ApplicationStateTree.obtain();
        for (ActivityObserver observer : activityObservers) {
            final ActivityNode activityNode = buildActivityNode(observer);
            tree.activities.add(activityNode);
        }
        return tree;
    }

    @NonNull
    private static ActivityNode buildActivityNode(@NonNull ActivityObserver observer) {
        final ActivityNode node = ActivityNode.obtain();
        node.activity = observer.activity;
        node.state = observer.state;
        final Activity activity = observer.activity;
        final List<FragmentNode> fragments = buildFragmentNodes(activity);
        if (Configuration.SHOW_VIEW_HIERARCHY) {
            final View contentView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (contentView instanceof ViewGroup) {
                final ViewGroup container = (ViewGroup) contentView;
                final int count = container.getChildCount();
                final Map<View, ViewNode> viewIndex = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    final ViewNode viewNode = buildViewNode(container.getChildAt(i), viewIndex);
                    node.views.add(viewNode);
                }
                mergeFragmentsIntoViews(fragments, viewIndex);
            }
        }
        node.fragment.addAll(fragments);
        return node;
    }

    @NonNull
    private static ViewNode buildViewNode(@NonNull View view, @NonNull Map<View, ViewNode> viewIndex) {
        final ViewNode node = ViewNode.obtain();
        node.view = view;
        viewIndex.put(view, node);
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                final ViewNode child = buildViewNode(group.getChildAt(i), viewIndex);
                node.child.add(child);
            }
        }
        return node;
    }

    @NonNull
    private static List<FragmentNode> buildFragmentNodes(@NonNull Activity activity) {
        if (activity instanceof FragmentActivity) {
            final FragmentManager manager = ((FragmentActivity) activity).getSupportFragmentManager();
            return buildFragmentNodes(manager);
        }
        return Collections.emptyList();
    }

    @NonNull
    private static List<FragmentNode> buildFragmentNodes(@NonNull FragmentManager manager) {
        final List<Fragment> fragments = manager.getFragments();
        final List<FragmentNode> nodes = new ArrayList<>(fragments.size());
        for (Fragment fragment : fragments) {
            final FragmentNode node = buildFragmentNode(fragment);
            nodes.add(node);
        }
        return nodes;
    }

    @NonNull
    private static FragmentNode buildFragmentNode(@NonNull Fragment fragment) {
        final FragmentNode node = FragmentNode.obtain();
        node.fragment = fragment;
        final FragmentManager childManager = fragment.getChildFragmentManager();
        for (Fragment child : childManager.getFragments()) {
            final FragmentNode childNode = buildFragmentNode(child);
            node.child.add(childNode);
        }
        return node;
    }

    private static void mergeFragmentsIntoViews(@NonNull List<FragmentNode> fragments,
                                                @NonNull Map<View, ViewNode> viewIndex) {
        for (int i = 0; i < fragments.size(); ) {
            boolean merged = false;
            final FragmentNode fragmentNode = fragments.get(i);
            final View fragmentView = fragmentNode.fragment.getView();
            if (fragmentView != null) {
                final ViewParent parent = fragmentView.getParent();
                if (parent instanceof View) {
                    final ViewNode parentViewNode = viewIndex.get(parent);
                    if (parentViewNode != null) {
                        // Insert fragment node between parent and fragment views
                        final ViewNode fragmentViewNode = viewIndex.get(fragmentView);
                        parentViewNode.child.remove(fragmentViewNode);
                        parentViewNode.fragments.add(fragmentNode);
                        fragmentNode.view = fragmentViewNode;
                        fragments.remove(i);
                        merged = true;
                    }
                }
            }
            mergeFragmentsIntoViews(fragmentNode.child, viewIndex);
            if (!merged) i++;
        }
    }

    // endregion

    // region Format Utils

    @NonNull
    private static OutputBuilder indent(@NonNull OutputBuilder builder, int level) {
        for (int i = 0; i < level; i++) {
            indent(builder, i % 2 == 0);
        }
        return builder;
    }

    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    private static OutputBuilder indent(@NonNull OutputBuilder builder, boolean anchor) {
        if (anchor) {
            return builder.append("⡇ ");
        } else {
            return builder.append("  ");
        }
    }

    @NonNull
    private static String formatClassLink(@NonNull Object obj) {
        final Class<?> cls = obj.getClass();
        final String className = cls.getSimpleName();
        final String sourceExt = getSourceFileExtension(cls);
        if (Configuration.APPEND_PACKAGES) {
            final Package classPackage = cls.getPackage();
            if (classPackage != null) {
                return String.format("%s.(%s.%s:0)", classPackage.getName(), className, sourceExt);
            }
        }
        return String.format(".(%s.%s:0)", className, sourceExt);
    }

    @NonNull
    private static String getSourceFileExtension(@NonNull Class<?> cls) {
        return isKotlinClass(cls) ? "kt" : "java";
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    private static String formatLocation(@NonNull Rect rect) {
        return String.format("[%d ⇔ %d]×[%d ⇕ %d]", rect.left, rect.right, rect.top, rect.bottom);
    }

    // endregion

    // region Common Utils

    private static boolean isAttachedToLayout(@NonNull Fragment fragment) {
        final View view = fragment.getView();
        if (view != null) {
            return view.getParent() != null;
        }
        return false;
    }

    private static boolean isDialogFragment(@NonNull Fragment fragment) {
        return fragment instanceof DialogFragment;
    }

    private static boolean isKotlinClass(@NonNull Class<?> cls) {
        for (Annotation annotation : cls.getDeclaredAnnotations()) {
            if ("kotlin.Metadata".equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    // Copy of internal View's class method
    private static boolean isViewIdGenerated(int id) {
        return (id & 0xFF000000) == 0 && (id & 0x00FFFFFF) != 0;
    }

    // endregion

    // region Recycle Utils

    @Nullable
    private static <T> T obtainElement(@NonNull List<T> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        return pool.remove(pool.size() - 1);
    }

    private static <T> void recycleElement(@NonNull List<T> pool, @NonNull T element) {
        pool.add(element);
    }

    private static void recycleAppStateTree(@NonNull ApplicationStateTree tree) {
        for (ActivityNode activityNode : tree.activities) {
            recycleActivityNode(activityNode);
        }
        tree.recycle();
    }

    private static void recycleActivityNode(@NonNull ActivityNode activityNode) {
        for (ViewNode viewNode : activityNode.views) {
            recycleViewNode(viewNode);
        }
        for (FragmentNode fragmentNode : activityNode.fragment) {
            recycleFragmentNode(fragmentNode);
        }
        activityNode.recycle();
    }

    private static void recycleViewNode(@NonNull ViewNode viewNode) {
        for (FragmentNode fragmentNode : viewNode.fragments) {
            recycleFragmentNode(fragmentNode);
        }
        for (ViewNode childNode : viewNode.child) {
            recycleViewNode(childNode);
        }
        viewNode.recycle();
    }

    private static void recycleFragmentNode(@NonNull FragmentNode fragmentNode) {
        for (FragmentNode childNode : fragmentNode.child) {
            recycleFragmentNode(childNode);
        }
        if (fragmentNode.view != null) {
            fragmentNode.view.recycle();
        }
        fragmentNode.recycle();
    }

    // endregion

    // region Entity Observers

    private static class ApplicationObserver implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            explore(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) { /* no-op */ }

        @Override
        public void onActivityResumed(Activity activity) { /* no-op */ }

        @Override
        public void onActivityPaused(Activity activity) { /* no-op */ }

        @Override
        public void onActivityStopped(Activity activity) { /* no-op */ }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) { /* no-op */ }

        @Override
        public void onActivityDestroyed(Activity activity) { /* no-op */ }
    }

    @SuppressWarnings("unused")
    private static class ActivityObserver implements LifecycleObserver {
        @NonNull
        final Activity activity;
        final ViewTreeObserver.OnGlobalLayoutListener layoutChangeListener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        notifyChangesDetected("layout changed");
                    }
                };
        @Nullable
        String state;

        ActivityObserver(@NonNull Activity activity) {
            this.activity = activity;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
        private void onCreate() {
            activityObservers.add(this);
            activity.getWindow().getDecorView().getViewTreeObserver()
                    .addOnGlobalLayoutListener(layoutChangeListener);
            state = "created";
            notifyStateChanged();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        private void onStart() {
            state = "started";
            notifyStateChanged();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        private void onResume() {
            state = "resumed";
            notifyStateChanged();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        private void onPause() {
            state = "paused";
            notifyStateChanged();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        private void onStop() {
            state = "stopped";
            notifyStateChanged();
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        private void onDestroy() {
            activity.getWindow().getDecorView().getViewTreeObserver()
                    .removeGlobalOnLayoutListener(layoutChangeListener);
            activityObservers.remove(this);
            activities.remove(activity);
            state = "destroyed";
            notifyStateChanged();
        }

        private void notifyStateChanged() {
            notifyChangesDetected(formatClassLink(activity) + " " + state);
        }
    }

    private static class FragmentObserver extends FragmentManager.FragmentLifecycleCallbacks {
        @Override
        public void onFragmentAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
            super.onFragmentAttached(fm, f, context);
            final Fragment parentFragment = f.getParentFragment();
            final String parent = parentFragment != null
                    ? parentFragment.getClass().getName()
                    : context.getClass().getName();
            notifyChangesDetected(formatClassLink(f) + " attached to " + parent);
        }

        @Override
        public void onFragmentDetached(@NonNull FragmentManager fm, @NonNull Fragment f) {
            super.onFragmentDetached(fm, f);
            notifyChangesDetected(formatClassLink(f) + " detached");
        }
    }

    // endregion

    // region Application State Structure

    private static class ApplicationStateTree {

        final List<ActivityNode> activities = new ArrayList<>();

        private static final List<ApplicationStateTree> pool = new ArrayList<>(2);

        private ApplicationStateTree() { }

        @NonNull
        static ApplicationStateTree obtain() {
            final ApplicationStateTree tree = obtainElement(pool);
            return tree != null ? tree : new ApplicationStateTree();
        }

        void recycle() {
            activities.clear();
            recycleElement(pool, this);
        }
    }

    private static class ActivityNode {

        Activity activity;

        String state;

        final List<ViewNode> views = new ArrayList<>();

        final List<FragmentNode> fragment = new ArrayList<>(1);

        private static final List<ActivityNode> pool = new ArrayList<>();

        private ActivityNode() { }

        @NonNull
        static ActivityNode obtain() {
            final ActivityNode node = obtainElement(pool);
            return node != null ? node : new ActivityNode();
        }

        void recycle() {
            activity = null;
            state = null;
            views.clear();
            fragment.clear();
            recycleElement(pool, this);
        }
    }

    private static class ViewNode {

        View view;

        final List<ViewNode> child = new ArrayList<>(5);

        final List<FragmentNode> fragments = new ArrayList<>(3);

        private static final List<ViewNode> pool = new ArrayList<>();

        private ViewNode() { }

        @NonNull
        static ViewNode obtain() {
            final ViewNode node = obtainElement(pool);
            return node != null ? node : new ViewNode();
        }

        void recycle() {
            view = null;
            child.clear();
            fragments.clear();
            recycleElement(pool, this);
        }
    }

    private static class FragmentNode {

        Fragment fragment;

        ViewNode view;

        final List<FragmentNode> child = new ArrayList<>(3);

        private static final List<FragmentNode> pool = new ArrayList<>();

        private FragmentNode() { }

        @NonNull
        static FragmentNode obtain() {
            final FragmentNode node = obtainElement(pool);
            return node != null ? node : new FragmentNode();
        }

        void recycle() {
            fragment = null;
            view = null;
            child.clear();
            recycleElement(pool, this);
        }
    }

    // endregion

    // region Output Builder

    private static class OutputBuilder {

        private final List<StringBuilder> messages = new ArrayList<>();
        private final List<String> line = new ArrayList<>();
        private StringBuilder message = new StringBuilder();
        private int messageBytes = 0;
        private int lineBytes = 0;

        @NonNull
        OutputBuilder append(@NonNull String string) {
            line.add(string);
            lineBytes += string.getBytes().length;
            return this;
        }

        @NonNull
        OutputBuilder newline() {
            line.add("\n");
            appendLine();
            return this;
        }

        @NonNull
        List<String> build() {
            if (!line.isEmpty()) {
                appendLine();
                appendMessage();
            }
            final List<String> output = new ArrayList<>(messages.size());
            for (StringBuilder message : messages) {
                output.add(message.toString());
            }
            return output;
        }

        private void appendLine() {
            if (messageBytes + lineBytes > LOGCAT_BUFFER_SIZE) {
                appendMessage();
            }
            for (String s : line) {
                message.append(s);
            }
            messageBytes += lineBytes;
            lineBytes = 0;
            line.clear();
        }

        private void appendMessage() {
            messages.add(message);
            message = new StringBuilder(" \n");
            messageBytes = 0;
        }

    }

    // endregion

}
