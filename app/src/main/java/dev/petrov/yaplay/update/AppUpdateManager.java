package dev.petrov.yaplay.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AppUpdateManager {
    public static final String PRIMARY_MANIFEST_URL =
            "https://raw.githubusercontent.com/reziarlleh/YMPlayer/main/update/manifest.json";
    public static final String ALTERNATIVE_MANIFEST_URL =
            "https://cdn.jsdelivr.net/gh/reziarlleh/YMPlayer@main/update/manifest.json";

    private static final String PREFS = "ymplayer_app_update";
    private static final String KEY_PENDING_APK = "pending_apk";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final long MAX_APK_BYTES = 100L * 1024L * 1024L;
    private static final int MAX_REDIRECTS = 5;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean CHECK_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean DOWNLOAD_RUNNING = new AtomicBoolean();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "YMP-AppUpdate");
        thread.setDaemon(true);
        return thread;
    });

    private AppUpdateManager() {
    }

    public static boolean checkAsync(Context context, CheckCallback callback) {
        if (!CHECK_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                UpdateInfo info = loadManifest(appContext);
                long installedVersionCode = installedVersionCode(appContext);
                MAIN.post(() -> callback.onResult(info, info.versionCode > installedVersionCode));
            } catch (Exception ex) {
                MAIN.post(() -> callback.onError(safeMessage(ex)));
            } finally {
                CHECK_RUNNING.set(false);
            }
        });
        return true;
    }

    public static boolean downloadAsync(
            Context context,
            UpdateInfo info,
            boolean preferAlternative,
            DownloadCallback callback
    ) {
        if (!DOWNLOAD_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            List<String> sources = orderedSources(info, preferAlternative);
            List<String> failures = new ArrayList<>();
            try {
                for (String source : sources) {
                    try {
                        File apk = downloadApk(appContext, info, source, callback);
                        boolean alternative = source.equals(info.alternativeApkUrl);
                        MAIN.post(() -> callback.onReady(apk, alternative));
                        return;
                    } catch (Exception ex) {
                        failures.add(host(source) + ": " + safeMessage(ex));
                    }
                }
                MAIN.post(() -> callback.onError(String.join("; ", failures)));
            } finally {
                DOWNLOAD_RUNNING.set(false);
            }
        });
        return true;
    }

    public static InstallResult requestInstall(Activity activity, File apk) throws Exception {
        if (apk == null || !apk.isFile()) {
            throw new IOException("Downloaded APK is missing");
        }
        rememberPendingApk(activity, apk);
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            try {
                activity.startActivity(permission);
            } catch (ActivityNotFoundException ex) {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
            return InstallResult.PERMISSION_REQUIRED;
        }
        launchInstaller(activity, apk);
        clearPendingApk(activity);
        return InstallResult.LAUNCHED;
    }

    public static boolean resumePendingInstall(Activity activity) throws Exception {
        File apk = pendingApk(activity);
        if (apk == null) {
            return false;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            return false;
        }
        launchInstaller(activity, apk);
        clearPendingApk(activity);
        return true;
    }

    private static UpdateInfo loadManifest(Context context) throws Exception {
        List<String> failures = new ArrayList<>();
        for (String source : new String[] {PRIMARY_MANIFEST_URL, ALTERNATIVE_MANIFEST_URL}) {
            try {
                String text = readText(source, MAX_MANIFEST_BYTES);
                return UpdateInfo.fromJson(new JSONObject(text), source, context.getPackageName());
            } catch (Exception ex) {
                failures.add(host(source) + ": " + safeMessage(ex));
            }
        }
        throw new IOException("Update sources unavailable: " + String.join("; ", failures));
    }

    private static File downloadApk(
            Context context,
            UpdateInfo info,
            String source,
            DownloadCallback callback
    ) throws Exception {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) {
            root = context.getFilesDir();
        }
        File directory = new File(root, "updates");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create update directory");
        }
        String safeVersion = info.versionName.replaceAll("[^A-Za-z0-9._-]", "_");
        File destination = new File(directory, "YMPlayer-v" + safeVersion + ".apk");
        if (destination.isFile() && verifyFile(destination, info)) {
            return destination;
        }

        File partial = new File(directory, destination.getName() + ".part");
        if (partial.exists() && !partial.delete()) {
            throw new IOException("Could not reset partial update");
        }

        HttpURLConnection connection = open(source);
        long declaredLength = connection.getContentLengthLong();
        if (declaredLength > MAX_APK_BYTES) {
            connection.disconnect();
            throw new IOException("APK is larger than the safety limit");
        }
        if (info.sizeBytes > 0 && declaredLength > 0 && declaredLength != info.sizeBytes) {
            connection.disconnect();
            throw new IOException("Unexpected APK size");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0L;
        int lastPercent = -1;
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_APK_BYTES) {
                    throw new IOException("APK is larger than the safety limit");
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                long expected = info.sizeBytes > 0 ? info.sizeBytes : declaredLength;
                int percent = expected > 0 ? (int) Math.min(100L, total * 100L / expected) : -1;
                if (percent != lastPercent && (percent < 0 || percent == 100 || percent - lastPercent >= 2)) {
                    lastPercent = percent;
                    int progress = percent;
                    MAIN.post(() -> callback.onProgress(progress, host(source)));
                }
            }
            output.getFD().sync();
        } catch (Exception ex) {
            partial.delete();
            throw ex;
        } finally {
            connection.disconnect();
        }

        if (info.sizeBytes > 0 && total != info.sizeBytes) {
            partial.delete();
            throw new IOException("Downloaded APK size does not match the manifest");
        }
        String actualSha256 = hex(digest.digest());
        if (!actualSha256.equalsIgnoreCase(info.sha256)) {
            partial.delete();
            throw new IOException("Downloaded APK checksum mismatch");
        }
        if (destination.exists() && !destination.delete()) {
            partial.delete();
            throw new IOException("Could not replace the previous update");
        }
        if (!partial.renameTo(destination)) {
            partial.delete();
            throw new IOException("Could not finalize the downloaded update");
        }
        return destination;
    }

    private static boolean verifyFile(File file, UpdateInfo info) {
        try {
            if (info.sizeBytes > 0 && file.length() != info.sizeBytes) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return hex(digest.digest()).equalsIgnoreCase(info.sha256);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readText(String source, int maxBytes) throws IOException {
        HttpURLConnection connection = open(source);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Update manifest is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String rawUrl) throws IOException {
        URL current = httpsUrl(rawUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*");
            connection.setRequestProperty("User-Agent", "YMPlayer-Android-Updater");
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                return connection;
            }
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307
                    || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect without a destination");
                }
                current = httpsUrl(new URL(current, location).toString());
                continue;
            }
            connection.disconnect();
            throw new IOException("HTTP " + code + " from " + current.getHost());
        }
        throw new IOException("Too many redirects");
    }

    private static URL httpsUrl(String rawUrl) throws IOException {
        URL url = new URL(rawUrl == null ? "" : rawUrl.trim());
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Only HTTPS update sources are allowed");
        }
        return url;
    }

    private static List<String> orderedSources(UpdateInfo info, boolean preferAlternative) {
        Set<String> sources = new LinkedHashSet<>();
        if (preferAlternative) {
            addSource(sources, info.alternativeApkUrl);
            addSource(sources, info.primaryApkUrl);
        } else {
            addSource(sources, info.primaryApkUrl);
            addSource(sources, info.alternativeApkUrl);
        }
        return new ArrayList<>(sources);
    }

    private static void addSource(Set<String> sources, String source) {
        if (source != null && !source.trim().isEmpty()) {
            sources.add(source.trim());
        }
    }

    private static long installedVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.getLongVersionCode();
    }

    private static void launchInstaller(Activity activity, File apk) throws Exception {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".update_files",
                apk
        );
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, APK_MIME);
        intent.setClipData(ClipData.newRawUri("YMPlayer update", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        List<ResolveInfo> handlers = activity.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo handler : handlers) {
            if (handler.activityInfo != null) {
                activity.grantUriPermission(
                        handler.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        }
        activity.startActivity(intent);
    }

    private static void rememberPendingApk(Context context, File apk) throws IOException {
        prefs(context).edit().putString(KEY_PENDING_APK, apk.getCanonicalPath()).apply();
    }

    private static File pendingApk(Context context) {
        String path = prefs(context).getString(KEY_PENDING_APK, "");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File apk = new File(path);
        if (!apk.isFile()) {
            clearPendingApk(context);
            return null;
        }
        return apk;
    }

    private static void clearPendingApk(Context context) {
        prefs(context).edit().remove(KEY_PENDING_APK).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String host(String source) {
        try {
            return new URL(source).getHost();
        } catch (Exception ignored) {
            return "update source";
        }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format(Locale.US, "%02x", item & 0xff));
        }
        return value.toString();
    }

    public interface CheckCallback {
        void onResult(UpdateInfo info, boolean updateAvailable);

        void onError(String error);
    }

    public interface DownloadCallback {
        void onProgress(int percent, String sourceHost);

        void onReady(File apk, boolean usedAlternative);

        void onError(String error);
    }

    public enum InstallResult {
        LAUNCHED,
        PERMISSION_REQUIRED
    }

    public static final class UpdateInfo {
        public final long versionCode;
        public final String versionName;
        public final int minSdk;
        public final String releaseNotes;
        public final String primaryApkUrl;
        public final String alternativeApkUrl;
        public final String sha256;
        public final long sizeBytes;
        public final String manifestUrl;

        private UpdateInfo(
                long versionCode,
                String versionName,
                int minSdk,
                String releaseNotes,
                String primaryApkUrl,
                String alternativeApkUrl,
                String sha256,
                long sizeBytes,
                String manifestUrl
        ) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.minSdk = minSdk;
            this.releaseNotes = releaseNotes;
            this.primaryApkUrl = primaryApkUrl;
            this.alternativeApkUrl = alternativeApkUrl;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.manifestUrl = manifestUrl;
        }

        public boolean hasAlternativeDownload() {
            return alternativeApkUrl != null && !alternativeApkUrl.trim().isEmpty();
        }

        private static UpdateInfo fromJson(JSONObject json, String manifestUrl, String installedPackage)
                throws Exception {
            if (json.optInt("schemaVersion", 1) != 1) {
                throw new IOException("Unsupported update manifest schema");
            }
            String packageName = json.optString("packageName", installedPackage).trim();
            if (!installedPackage.equals(packageName)) {
                throw new IOException("Update package does not match YMPlayer");
            }
            long versionCode = json.optLong("versionCode", 0L);
            String versionName = json.optString("versionName", "").trim();
            int minSdk = json.optInt("minSdk", 29);
            JSONObject apk = json.optJSONObject("apk");
            if (versionCode <= 0 || versionName.isEmpty() || apk == null) {
                throw new IOException("Update manifest is incomplete");
            }
            if (minSdk > Build.VERSION.SDK_INT) {
                throw new IOException("Update requires Android API " + minSdk);
            }
            String primary = apk.optString("primaryUrl", "").trim();
            String alternative = apk.optString("alternativeUrl", "").trim();
            String sha256 = apk.optString("sha256", "").trim().toLowerCase(Locale.US);
            long sizeBytes = apk.optLong("sizeBytes", 0L);
            if (primary.isEmpty() && alternative.isEmpty()) {
                throw new IOException("Update manifest has no APK URL");
            }
            if (!primary.isEmpty()) {
                httpsUrl(primary);
            }
            if (!alternative.isEmpty()) {
                httpsUrl(alternative);
            }
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IOException("Update manifest has no valid SHA-256");
            }
            if (sizeBytes < 0 || sizeBytes > MAX_APK_BYTES) {
                throw new IOException("Update manifest has an invalid APK size");
            }
            return new UpdateInfo(
                    versionCode,
                    versionName,
                    minSdk,
                    json.optString("releaseNotes", "").trim(),
                    primary,
                    alternative,
                    sha256,
                    sizeBytes,
                    manifestUrl
            );
        }
    }
}
