package com.novelex.dramas;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Animated edge-attached liquid feedback for timeline scrubbing. Optimized for zero allocations in onDraw. */
public final class LiquidSeekView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Typeface fontMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private float amount, target, center, targetCenter;
    private boolean right;
    private String line1 = "";
    private String line2 = "";
    private final float dp;

    private LinearGradient cachedGradient;
    private float lastGradientDepth = -1f;

    public LiquidSeekView(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        paint.setTypeface(fontMedium);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void update(long delta, long duration, long position, float y) {
        right = delta < 0;
        target = 0.28f + 0.72f * Math.min(1f, Math.abs(delta) / (float) Math.max(1L, duration));
        targetCenter = getHeight() / 2f;
        center = targetCenter;
        line1 = (delta >= 0 ? "+" : "−") + android.text.format.DateUtils.formatElapsedTime(Math.abs(delta) / 1000);
        line2 = android.text.format.DateUtils.formatElapsedTime(position / 1000);
        invalidate();
    }

    public void retract() { target = 0; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        amount += (target - amount) * 0.22f;
        center += (targetCenter - center) * 0.18f;
        if (amount < 0.002f && target == 0) { amount = 0; return; }
        float depth = Math.min(getWidth() * 0.42f, 200 * dp) * amount;
        float radius = (65 + 95 * amount) * dp;
        float cy = getHeight() / 2f;
        canvas.save();
        if (right) { canvas.translate(getWidth(), 0); canvas.scale(-1, 1); }
        path.reset(); path.moveTo(0, cy - radius);
        path.cubicTo(0, cy - radius * 0.35f, depth, cy - radius * 0.6f, depth, cy);
        path.cubicTo(depth, cy + radius * 0.6f, 0, cy + radius * 0.35f, 0, cy + radius);
        path.close();

        if (cachedGradient == null || Math.abs(depth - lastGradientDepth) > 2f) {
            cachedGradient = new LinearGradient(0, 0, Math.max(1f, depth), 0, 0xE69477E6, 0xF0EA73B4, Shader.TileMode.CLAMP);
            lastGradientDepth = depth;
        }
        paint.setShader(cachedGradient);
        paint.setStyle(Paint.Style.FILL); canvas.drawPath(path, paint);
        paint.setShader(null); canvas.restore();

        if (depth > 35 * dp) {
            paint.setColor(Color.WHITE); paint.setAlpha((int) (255 * Math.min(1, amount / 0.28f)));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12 * dp);
            float x = right ? getWidth() - depth * 0.45f : depth * 0.45f;
            canvas.drawText(line1, x, cy - 5 * dp, paint);
            canvas.drawText(line2, x, cy + 13 * dp, paint);
            paint.setAlpha(255);
        }
        if (Math.abs(target - amount) > 0.002f || Math.abs(targetCenter - center) > 0.5f) postInvalidateOnAnimation();
    }
}
