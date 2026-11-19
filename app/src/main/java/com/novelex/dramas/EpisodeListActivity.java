package com.novelex.dramas;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;

public class EpisodeListActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_episode_list);
        Drama drama = (Drama) getIntent().getSerializableExtra("drama");
        if (drama == null) { finish(); return; }
        int current = getIntent().getIntExtra("current", 0);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.episodeListRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        findViewById(R.id.episodeListBack).setOnClickListener(v -> finish());
        ((TextView)findViewById(R.id.episodeListTitle)).setText(drama.getTitle());
        ((TextView)findViewById(R.id.episodeListCount)).setText(drama.getEpisodes().size() + getString(R.string.ui_54) + (current + 1));
        RecyclerView list = findViewById(R.id.episodeList);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        list.setLayoutManager(manager);
        list.setAdapter(new EpisodeSheetAdapter(this, drama.getId(), drama.getEpisodes(), current,
            new EpisodeAccess(getSharedPreferences("novelex_prefs", MODE_PRIVATE), drama.getId()), (index, locked) -> {
                setResult(RESULT_OK, new Intent().putExtra("episode_index", index)); finish();
            }));
        manager.scrollToPositionWithOffset(current, 0);
    }
}
