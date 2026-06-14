package dev.petrov.yaplay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.petrov.yaplay.poweramp.YaPlayProvider;

public class YaPlayTreePickerActivity extends Activity {
    private static final int RESULT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diagnostics.log(this, "YaPlay tree picker opened by " + safeCaller());
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(R.string.tree_picker_title);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.tree_picker_subtitle);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle, matchWrap());

        addButton(root, R.string.tree_picker_all, v -> finishWithTree(YaPlayProvider.rootDocumentId()));
        addButton(root, R.string.tree_picker_downloaded_liked, v -> finishWithTree(YaPlayProvider.cacheDocumentId()));
        addButton(root, R.string.tree_picker_my_wave, v -> finishWithTree(YaPlayProvider.waveDocumentId()));
        addButton(root, R.string.tree_picker_liked, v -> finishWithTree(YaPlayProvider.likedDocumentId()));
        addButton(root, R.string.tree_picker_cancel, v -> {
            Diagnostics.log(this, "YaPlay tree picker cancelled");
            setResult(RESULT_CANCELED);
            finish();
        });

        return scroll;
    }

    private void finishWithTree(String documentId) {
        Uri treeUri = YaPlayProvider.treeUri(this, documentId);
        Intent result = new Intent();
        result.setData(treeUri);
        result.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        result.addFlags(RESULT_FLAGS);
        String caller = getCallingPackage();
        if (caller != null && !caller.isEmpty()) {
            grantUriPermission(caller, treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        Diagnostics.log(this, "YaPlay tree selected: " + documentId + ", uri=" + treeUri);
        setResult(RESULT_OK, result);
        finish();
    }

    private Button addButton(LinearLayout root, int titleRes, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(titleRes);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        root.addView(button, spaced());
        return button;
    }

    private String safeCaller() {
        String caller = getCallingPackage();
        return caller == null || caller.isEmpty() ? "unknown caller" : caller;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }
}
