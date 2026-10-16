package com.orvid.ui;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.orvid.R;
import com.orvid.app.OrvidApp;

public final class LockActivity extends Activity {

    private static final int CONFIRM = 1;
    private boolean asking;
    private boolean autoAsked;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackgroundColor(getColor(R.color.bg));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_lock);
        column.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 63), Ui.dp(this, 72)));

        TextView title = new TextView(this);
        title.setText(R.string.lock_title);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 24));
        column.addView(title);

        TextView unlock = new TextView(this);
        unlock.setText(R.string.lock_unlock);
        unlock.setTextColor(getColor(R.color.on_accent));
        unlock.setTextSize(15);
        unlock.setGravity(Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(getColor(R.color.accent));
        pill.setCornerRadius(Ui.dp(this, 24));
        unlock.setBackground(pill);
        unlock.setPadding(Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12));
        unlock.setOnClickListener(v -> ask());
        column.addView(unlock, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(column);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoAsked) return;
        autoAsked = true;
        ask();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!asking) autoAsked = false;
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void ask() {
        if (asking) return;
        asking = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this)
                    .setTitle(getString(R.string.lock_prompt));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                builder.setDeviceCredentialAllowed(true);
            }
            builder.build().authenticate(new CancellationSignal(), getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            done();
                        }

                        @Override
                        public void onAuthenticationError(int code, CharSequence message) {
                            asking = false;
                        }
                    });
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        Intent confirm = keyguard == null ? null : keyguard.createConfirmDeviceCredentialIntent(getString(R.string.lock_prompt), null);
        if (confirm == null) {
            done();
            return;
        }
        startActivityForResult(confirm, CONFIRM);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CONFIRM) return;
        if (result == RESULT_OK) done();
        else asking = false;
    }

    private void done() {
        OrvidApp.get().lock().unlocked();
        finish();
        overridePendingTransition(0, 0);
    }
}
