package io.github.endless_sky.endless_sky;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

/**
 * Endless Sky Android entry point. SDLActivity loads the "SDL2" and "main"
 * native libraries and calls SDL_main (Endless Sky's main()).
 *
 * A loading overlay is shown on top of the SDL surface from launch until the native
 * code draws its first frame. This covers native init and the one-time, first-run
 * asset extraction (which happen before any OpenGL context exists, so the engine's
 * own loading panel cannot cover them). Native code calls {@link #hideSplash()} once
 * the first frame is on screen.
 */
public class MainActivity extends SDLActivity {
    private static MainActivity instance;
    private View splash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("ENDLESS SKY");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setLetterSpacing(0.15f);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        LinearLayout.LayoutParams below = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        below.topMargin = 48;
        below.gravity = Gravity.CENTER_HORIZONTAL;

        TextView label = new TextView(this);
        label.setText("Loading…");
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        box.addView(label, below);

        ProgressBar spinner = new ProgressBar(this);
        box.addView(spinner, below);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xFF05070F);
        overlay.setClickable(true);
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        center.gravity = Gravity.CENTER;
        overlay.addView(box, center);

        addContentView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash = overlay;
    }

    /** Called from native code (main.cpp) once the first frame has been drawn. */
    public static void hideSplash() {
        final MainActivity activity = instance;
        if (activity == null)
            return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.splash != null) {
                    ViewGroup parent = (ViewGroup) activity.splash.getParent();
                    if (parent != null)
                        parent.removeView(activity.splash);
                    activity.splash = null;
                }
            }
        });
    }

    /** Called from native code for a short haptic tick on on-screen button presses. */
    public static void vibrate() {
        final MainActivity activity = instance;
        if (activity == null)
            return;
        Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator())
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
        else
            vibrator.vibrate(15);
    }
}
