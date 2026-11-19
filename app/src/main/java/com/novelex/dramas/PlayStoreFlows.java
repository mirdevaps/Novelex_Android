package com.novelex.dramas;

import android.app.Activity;
import android.content.SharedPreferences;
import com.google.android.play.core.appupdate.*;
import com.google.android.play.core.install.model.*;
import com.google.android.play.core.review.*;

final class PlayStoreFlows {
    static final int UPDATE_REQUEST = 701;
    private final Activity activity;
    private final AppUpdateManager updates;
    private boolean checking;

    PlayStoreFlows(Activity activity) {
        this.activity = activity;
        updates = AppUpdateManagerFactory.create(activity);
    }

    void check() {
        if (checking) return;
        checking = true;
        updates.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (activity.isFinishing() || activity.isDestroyed()) { checking = false; return; }
            boolean available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                || info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS;
            if (available && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                try {
                    boolean launched = updates.startUpdateFlowForResult(info, activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(), UPDATE_REQUEST);
                    if (!launched) checking = false;
                } catch (android.content.IntentSender.SendIntentException error) {
                    checking = false;
                    android.util.Log.w("NovelexPlay", "Update launch failed", error);
                }
            } else {
                checking = false;
                if (!available) requestReviewIfEligible();
            }
        }).addOnFailureListener(error -> {
            checking = false;
            android.util.Log.w("NovelexPlay", "Update check unavailable", error);
        });
    }

    void updateResult(int result) {
        checking = false;
        if (result != Activity.RESULT_OK) activity.finish();
    }

    private void requestReviewIfEligible() {
        SharedPreferences prefs = activity.getSharedPreferences("novelex_prefs", Activity.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (prefs.getStringSet("review_completed_episodes", java.util.Collections.emptySet()).size() < 3
            || now - prefs.getLong("review_last_request", 0) < 90L * 24 * 60 * 60 * 1000) return;
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnSuccessListener(info -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            prefs.edit().putLong("review_last_request", System.currentTimeMillis()).apply();
            manager.launchReviewFlow(activity, info);
        }).addOnFailureListener(error -> android.util.Log.w("NovelexPlay", "Review unavailable", error));
    }
}
