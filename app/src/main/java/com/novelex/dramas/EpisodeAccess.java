package com.novelex.dramas;

import android.content.SharedPreferences;
import java.util.*;

final class EpisodeAccess {
    private final SharedPreferences prefs;
    private final String key;
    private final int legacyLimit;
    EpisodeAccess(SharedPreferences prefs, String dramaId) {
        this.prefs = prefs;
        key = "unlocked_episodes_v2_" + dramaId;
        // Preserve previously granted access; new rewards never extend this boundary.
        legacyLimit = prefs.getInt("unlocked_" + dramaId, 2);
    }
    boolean isUnlocked(int number) {
        return number <= legacyLimit || prefs.getStringSet(key, Collections.emptySet()).contains(String.valueOf(number));
    }
    void grant(List<Episode> episodes, int selected) {
        Set<String> granted = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
        for (int i = selected; i < Math.min(episodes.size(), selected + 2); i++) {
            granted.add(String.valueOf(episodes.get(i).getNumber()));
        }
        prefs.edit().putStringSet(key, granted).apply();
    }
}
