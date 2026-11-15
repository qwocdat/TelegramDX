package org.telegram.ui.LiquidGlass;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.messenger.AndroidUtilities;

/**
 * Thanh TabBar Liquid Glass nâng cấp cho TelegramDX:
 * - Chuyển 2 nút Chụp ảnh nhanh (Camera) và Tạo nhóm mới (New Group/Chat) xuống 2 bên cạnh của thanh TabBar.
 * - Tích hợp AGSL Shader thật (Lens Refraction + 7-band Chromatic Aberration + Smooth-Minimum Gooey Metaball SDF).
 * - Hiệu ứng cuộn 3 giai đoạn liền mạch theo ngón tay (Interactive Scroll Progress p in [0..1]):
 *   + Giai đoạn 1 (p = 0.00 -> 0.30): Chữ dưới các tab mờ dần và thu nhỏ, chỉ còn icon của các tab.
 *   + Giai đoạn 2 (p = 0.30 -> 0.65): Nút Tạo nhóm bên phải từ từ tiến lại gần và hòa quyện vào thanh TabBar
 *     như 2 giọt nước chạm nhau (Metaball neck), đi theo đúng độ vuốt của ngón tay.
 *   + Giai đoạn 3 (p = 0.65 -> 1.00): Thanh TabBar tiếp tục thu gọn và hòa quyện vào nút Camera bên trái,
 *     biến hình mượt mà thành nút Menu 3 gạch (Liquid Glass Hamburger Pill).
 * - 2 cách tách ra lại như bình thường:
 *   + Cách 1: Bấm trực tiếp vào nút 3 gạch -> bung ngược ra với hiệu ứng giọt nước tách rời đàn hồi (Spring).
 *   + Cách 2: Vuốt lên ngược lại trên màn hình -> tách dần ra theo đúng hành trình ngón tay.
 */
public class LiquidGlassTabBarOverlay extends View implements SensorEventListener {

    public interface Callback {
        void onTabSelected(int index);
        void onQuickCameraClicked();
        void onNewGroupClicked();
        void onOpenDXSettings();
    }

    private final String[] tabLabels = new String[]{"Liên hệ", "Cuộc gọi", "Tin nhắn", "Cài đặt"};
    private int selectedTabIndex = 2; // Mặc định tab Tin nhắn
    private float animatedTabIndex = 2f;
    private float pressProgress = 0f;
    private float collapseProgress = 0f; // 0f = mở hoàn toàn, 1f = gộp thành nút 3 gạch

    private SpringAnimation tabIndexSpring;
    private SpringAnimation pressSpring;
    private SpringAnimation collapseSpring;

    private final Paint fallbackGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path metaballPath = new Path();

    private Bitmap backdropBitmap;
    private Canvas backdropCanvas;
    private RuntimeShader metaballShader;
    private boolean isCapturingBackdrop = false;

    private float lightDirX = 0.45f;
    private float lightDirY = -0.85f;
    private SensorManager sensorManager;

    private final RectF centerPillRect = new RectF();
    private float leftCenterX, leftCenterY, leftRadius;
    private float rightCenterX, rightCenterY, rightRadius;

    private boolean isDraggingTab = false;
    private float downX, downY;
    private VelocityTracker velocityTracker;
    private Callback callback;

    public LiquidGlassTabBarOverlay(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(AndroidUtilities.dp(2.1f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(Color.WHITE);

        textPaint.setTextSize(AndroidUtilities.dp(11f));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(false);

        rimStrokePaint.setStyle(Paint.Style.STROKE);
        rimStrokePaint.setStrokeWidth(AndroidUtilities.dp(1.2f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                metaballShader = LiquidGlassShaders.createTabBarMetaballShader();
            } catch (Throwable ignored) {
            }
        }

        FloatValueHolder tabHolder = new FloatValueHolder(animatedTabIndex * 1000f);
        tabIndexSpring = new SpringAnimation(tabHolder);
        SpringForce tabForce = new SpringForce(animatedTabIndex * 1000f);
        tabForce.setDampingRatio(0.68f);
        tabForce.setStiffness(320f);
        tabIndexSpring.setSpring(tabForce);
        tabIndexSpring.addUpdateListener((anim, val, vel) -> {
            animatedTabIndex = val / 1000f;
            invalidate();
        });

        FloatValueHolder pressHolder = new FloatValueHolder(0f);
        pressSpring = new SpringAnimation(pressHolder);
        SpringForce pressForce = new SpringForce(0f);
        pressForce.setDampingRatio(0.58f);
        pressForce.setStiffness(420f);
        pressSpring.setSpring(pressForce);
        pressSpring.addUpdateListener((anim, val, vel) -> {
            pressProgress = val / 1000f;
            invalidate();
        });

        FloatValueHolder collapseHolder = new FloatValueHolder(0f);
        collapseSpring = new SpringAnimation(collapseHolder);
        SpringForce collapseForce = new SpringForce(0f);
        collapseForce.setDampingRatio(0.64f);
        collapseForce.setStiffness(260f);
        collapseSpring.setSpring(collapseForce);
        collapseSpring.addUpdateListener((anim, val, vel) -> {
            collapseProgress = Math.max(0f, Math.min(1f, val / 1000f));
            invalidate();
        });

        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        } catch (Throwable ignored) {
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public float getCollapseProgress() {
        return collapseProgress;
    }

    /**
     * Cập nhật tiến trình cuộn liên tục theo ngón tay (dy > 0 là vuốt xuống, dy < 0 là vuốt lên).
     */
    public void onContentScrollDelta(float dyPx) {
        if (!DXModConfig.gooeyTabBarEnabled) return;
        if (collapseSpring.isRunning()) {
            collapseSpring.cancel();
        }
        float rangePx = AndroidUtilities.dp(260f);
        float next = collapseProgress + (dyPx / rangePx);
        collapseProgress = Math.max(0f, Math.min(1f, next));
        invalidate();
    }

    /**
     * Khi người dùng thả tay sau khi cuộn, nếu đang ở ngưỡng lưng chừng thì đàn hồi mượt mà
     * nhưng vẫn giữ khả năng vuốt theo tay ở mọi điểm.
     */
    public void settleScrollStateIfNeeded() {
        if (collapseProgress > 0.86f && collapseProgress < 1f) {
            animateCollapseTo(1f);
        } else if (collapseProgress > 0f && collapseProgress < 0.08f) {
            animateCollapseTo(0f);
        }
    }

    public void animateCollapseTo(float target) {
        target = Math.max(0f, Math.min(1f, target));
        collapseSpring.setStartValue(collapseProgress * 1000f);
        collapseSpring.getSpring().setFinalPosition(target * 1000f);
        collapseSpring.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sensorManager != null) {
            Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (gravity != null) {
                sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            float gx = event.values[0];
            float gy = event.values[1];
            float mag = (float) Math.sqrt(gx * gx + gy * gy);
            if (mag > 0.3f) {
                lightDirX = gx / mag;
                lightDirY = -gy / mag;
                invalidate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void computeGeometry(int w, int h) {
        float padH = AndroidUtilities.dp(14f);
        float barH = AndroidUtilities.dp(60f);
        float cy = h - AndroidUtilities.dp(24f) - barH * 0.5f;
        float sideR = barH * 0.46f;
        float gap = AndroidUtilities.dp(10f);

        // Giai đoạn 1: 0.00 -> 0.30 (ẩn chữ, chỉ còn icon)
        // Giai đoạn 2: 0.30 -> 0.65 (nút Tạo nhóm bên phải tiến vào và gộp giọt nước với TabBar)
        // Giai đoạn 3: 0.65 -> 1.00 (cả khối TabBar thu về nút Camera bên trái và biến thành nút Menu 3 gạch)
        float pRight = clamp01((collapseProgress - 0.30f) / 0.35f);
        float pLeft = clamp01((collapseProgress - 0.65f) / 0.35f);

        float initialLeftX = padH + sideR;
        float initialRightX = w - padH - sideR;

        float initialMainLeft = initialLeftX + sideR + gap;
        float initialMainRight = initialRightX - sideR - gap;

        // Khi pRight tăng, nút bên phải bị hút vào cạnh phải của TabBar và cạnh phải TabBar co nhẹ lại
        float mergedMainRight = initialMainRight - AndroidUtilities.dp(12f) * pRight;
        float currentRightX = lerp(initialRightX, mergedMainRight - sideR * 0.65f, smoothstep(pRight));
        float currentRightR = sideR * lerp(1f, 0.72f, pRight);

        // Khi pLeft tăng (0.65 -> 1.0), toàn bộ thanh TabBar co dần về tâm nút Camera bên trái
        float finalMainLeft = initialLeftX - sideR;
        float finalMainRight = initialLeftX + sideR;

        float curMainLeft = lerp(initialMainLeft, finalMainLeft, smoothstep(pLeft));
        float curMainRight = lerp(mergedMainRight, finalMainRight, smoothstep(pLeft));

        centerPillRect.set(curMainLeft, cy - barH * 0.48f * (1f - 0.08f * pLeft), curMainRight, cy + barH * 0.48f * (1f - 0.08f * pLeft));

        leftCenterX = initialLeftX;
        leftCenterY = cy;
        leftRadius = sideR * (1f + 0.06f * (float) Math.sin(pLeft * Math.PI));

        rightCenterX = lerp(currentRightX, leftCenterX, smoothstep(pLeft));
        rightCenterY = cy;
        rightRadius = currentRightR * (1f - 0.85f * pLeft);
    }

    private void captureUnderlyingBackdrop() {
        if (isCapturingBackdrop) return;
        View root = getRootView();
        if (root == null || getWidth() <= 0 || getHeight() <= 0) return;

        int downscale = 2;
        int bw = Math.max(1, getWidth() / downscale);
        int bh = Math.max(1, getHeight() / downscale);
        if (backdropBitmap == null || backdropBitmap.getWidth() != bw || backdropBitmap.getHeight() != bh) {
            backdropBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            backdropCanvas = new Canvas(backdropBitmap);
        }

        int[] loc = new int[2];
        getLocationInWindow(loc);

        isCapturingBackdrop = true;
        try {
            backdropCanvas.save();
            backdropCanvas.scale(1f / downscale, 1f / downscale);
            backdropCanvas.translate(-loc[0], -loc[1]);
            root.draw(backdropCanvas);
            backdropCanvas.restore();
        } catch (Throwable ignored) {
        } finally {
            isCapturingBackdrop = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isCapturingBackdrop || !DXModConfig.liquidGlassEnabled) return;

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        computeGeometry(w, h);

        float pText = 1f - clamp01(collapseProgress / 0.30f);
        float pRight = clamp01((collapseProgress - 0.30f) / 0.35f);
        float pLeft = clamp01((collapseProgress - 0.65f) / 0.35f);

        float tabCount = tabLabels.length;
        float pillW = Math.max(1f, centerPillRect.width());
        float singleTabW = pillW / tabCount;
        float indCenterX = centerPillRect.left + (animatedTabIndex + 0.5f) * singleTabW;
        float indHalfW = singleTabW * 0.46f * (1f + 0.14f * pressProgress) * (1f - pLeft);

        boolean drewShader = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && metaballShader != null) {
            captureUnderlyingBackdrop();
            if (backdropBitmap != null) {
                try {
                    BitmapShader bmpShader = new BitmapShader(backdropBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix m = new Matrix();
                    m.setScale(2f, 2f);
                    bmpShader.setLocalMatrix(m);

                    metaballShader.setInputShader("content", bmpShader);
                    metaballShader.setFloatUniform("resolution", (float) w, (float) h);
                    metaballShader.setFloatUniform(
                        "centerPill",
                        centerPillRect.centerX(),
                        centerPillRect.centerY(),
                        Math.max(1f, centerPillRect.width() * 0.5f),
                        Math.max(1f, centerPillRect.height() * 0.5f)
                    );
                    metaballShader.setFloatUniform("leftDrop", leftCenterX, leftCenterY, leftRadius);
                    metaballShader.setFloatUniform("rightDrop", rightCenterX, rightCenterY, Math.max(0.1f, rightRadius));
                    metaballShader.setFloatUniform("indicatorPos", indCenterX, indHalfW);
                    metaballShader.setFloatUniform("gooeyK", AndroidUtilities.dp(26f));
                    metaballShader.setFloatUniform("refractionHeight", AndroidUtilities.dp(DXModConfig.refractionHeightDp));
                    metaballShader.setFloatUniform("refractionAmount", AndroidUtilities.dp(DXModConfig.refractionAmountDp));
                    metaballShader.setFloatUniform("chromaticAberration", DXModConfig.chromaticAberration);
                    metaballShader.setFloatUniform("lightDir", lightDirX, lightDirY);
                    metaballShader.setFloatUniform("pressProgress", pressProgress);
                    metaballShader.setFloatUniform("rightMergeAlpha", 1f - 0.95f * pLeft);
                    metaballShader.setFloatUniform("leftMergeAlpha", 1f);
                    metaballShader.setFloatUniform("surfaceTint", 0.08f, 0.11f, 0.16f, 0.34f);

                    shaderPaint.setShader(metaballShader);
                    float topBound = Math.min(centerPillRect.top, leftCenterY - leftRadius) - AndroidUtilities.dp(36f);
                    canvas.drawRect(0, Math.max(0, topBound), w, h, shaderPaint);
                    drewShader = true;
                } catch (Throwable ignored) {
                }
            }
        }

        if (!drewShader) {
            drawFallbackMetaballGlass(canvas, pRight, pLeft);
        }

        // Vẽ nội dung bên trong các nút và thanh TabBar
        drawRightNewGroupIcon(canvas, 1f - smoothstep(clamp01((pRight - 0.35f) / 0.55f)));
        drawCenterTabItems(canvas, pText, 1f - smoothstep(clamp01((pLeft - 0.15f) / 0.65f)));
        drawLeftCameraOrMenuIcon(canvas, smoothstep(pLeft));
    }

    private void drawFallbackMetaballGlass(Canvas canvas, float pRight, float pLeft) {
        fallbackGlassPaint.setColor(0xCC18202C);
        float r = centerPillRect.height() * 0.5f;
        canvas.drawRoundRect(centerPillRect, r, r, fallbackGlassPaint);
        canvas.drawCircle(leftCenterX, leftCenterY, leftRadius, fallbackGlassPaint);
        if (pLeft < 0.9f) {
            canvas.drawCircle(rightCenterX, rightCenterY, rightRadius, fallbackGlassPaint);
        }
        // Cầu nối giọt nước (Metaball neck) khi 2 khối chạm nhau
        if (pRight > 0.08f && pRight < 0.92f && pLeft < 0.5f) {
            float neckH = leftRadius * 0.65f * (float) Math.sin(pRight * Math.PI);
            canvas.drawRect(centerPillRect.right - r * 0.5f, rightCenterY - neckH, rightCenterX, rightCenterY + neckH, fallbackGlassPaint);
        }
        if (pLeft > 0.05f && pLeft < 0.92f) {
            float neckH = leftRadius * 0.72f * (float) Math.sin(pLeft * Math.PI);
            canvas.drawRect(leftCenterX, leftCenterY - neckH, centerPillRect.left + r * 0.5f, leftCenterY + neckH, fallbackGlassPaint);
        }
        rimStrokePaint.setShader(new LinearGradient(
            0, centerPillRect.top, w(), centerPillRect.bottom,
            new int[]{0x99FFFFFF, 0x3388CCFF, 0x88FFFFFF},
            null, Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(centerPillRect, r, r, rimStrokePaint);
        canvas.drawCircle(leftCenterX, leftCenterY, leftRadius, rimStrokePaint);
    }

    private int w() {
        return Math.max(1, getWidth());
    }

    private void drawCenterTabItems(Canvas canvas, float textAlpha, float tabContentAlpha) {
        if (tabContentAlpha <= 0.02f) return;
        int count = tabLabels.length;
        float tabW = centerPillRect.width() / count;
        float cy = centerPillRect.centerY();

        for (int i = 0; i < count; i++) {
            float cx = centerPillRect.left + (i + 0.5f) * tabW;
            float selectProximity = Math.max(0f, 1f - Math.abs(animatedTabIndex - i));
            // Khi chữ ẩn đi (textAlpha -> 0), icon tự căn giữa theo chiều dọc của thanh TabBar
            float iconOffsetY = lerp(0f, -AndroidUtilities.dp(7f), textAlpha);
            float iconCy = cy + iconOffsetY;
            float scale = 1f + 0.14f * selectProximity;

            canvas.save();
            canvas.translate(cx, iconCy);
            canvas.scale(scale, scale);

            int alphaInt = (int) (255 * tabContentAlpha * (0.68f + 0.32f * selectProximity));
            if (selectProximity > 0.5f) {
                iconPaint.setColor(Color.argb(alphaInt, 95, 185, 255));
            } else {
                iconPaint.setColor(Color.argb(alphaInt, 240, 246, 255));
            }

            float s = AndroidUtilities.dp(9.5f);
            switch (i) {
                case 0: // Liên hệ (Contacts icon)
                    canvas.drawCircle(0, -s * 0.35f, s * 0.42f, iconPaint);
                    RectF arc = new RectF(-s * 0.8f, s * 0.15f, s * 0.8f, s * 1.2f);
                    canvas.drawArc(arc, 200f, 140f, false, iconPaint);
                    break;
                case 1: // Cuộc gọi (Phone icon)
                    RectF phoneRect = new RectF(-s * 0.7f, -s * 0.7f, s * 0.7f, s * 0.7f);
                    canvas.drawArc(phoneRect, 125f, 200f, false, iconPaint);
                    break;
                case 2: // Tin nhắn (Chat bubble icon)
                    RectF chatRect = new RectF(-s * 0.85f, -s * 0.7f, s * 0.85f, s * 0.55f);
                    canvas.drawRoundRect(chatRect, s * 0.4f, s * 0.4f, iconPaint);
                    break;
                case 3: // Cài đặt (Gear icon)
                    canvas.drawCircle(0, 0, s * 0.48f, iconPaint);
                    for (int k = 0; k < 6; k++) {
                        double ang = k * Math.PI / 3.0;
                        canvas.drawLine(
                            (float) Math.cos(ang) * s * 0.58f,
                            (float) Math.sin(ang) * s * 0.58f,
                            (float) Math.cos(ang) * s * 0.86f,
                            (float) Math.sin(ang) * s * 0.86f,
                            iconPaint
                        );
                    }
                    break;
            }
            canvas.restore();

            if (textAlpha > 0.02f) {
                int tAlpha = (int) (255 * textAlpha * tabContentAlpha * (0.65f + 0.35f * selectProximity));
                if (selectProximity > 0.5f) {
                    textPaint.setColor(Color.argb(tAlpha, 110, 195, 255));
                } else {
                    textPaint.setColor(Color.argb(tAlpha, 235, 242, 255));
                }
                canvas.save();
                float textY = cy + AndroidUtilities.dp(15.5f) + (1f - textAlpha) * AndroidUtilities.dp(6f);
                canvas.scale(0.85f + 0.15f * textAlpha, 0.85f + 0.15f * textAlpha, cx, textY);
                canvas.drawText(tabLabels[i], cx, textY, textPaint);
                canvas.restore();
            }
        }
    }

    private void drawRightNewGroupIcon(Canvas canvas, float alpha) {
        if (alpha <= 0.02f) return;
        int a = (int) (255 * alpha);
        iconPaint.setColor(Color.argb(a, 245, 250, 255));
        float s = AndroidUtilities.dp(8.5f);
        canvas.save();
        canvas.translate(rightCenterX, rightCenterY);
        float sc = 0.6f + 0.4f * alpha;
        canvas.scale(sc, sc);
        // Biểu tượng Tạo nhóm mới / Compose (+)
        canvas.drawCircle(-s * 0.25f, -s * 0.25f, s * 0.42f, iconPaint);
        RectF body = new RectF(-s * 0.9f, s * 0.25f, s * 0.4f, s * 1.15f);
        canvas.drawArc(body, 200f, 140f, false, iconPaint);
        canvas.drawLine(s * 0.55f, -s * 0.5f, s * 0.55f, s * 0.2f, iconPaint);
        canvas.drawLine(s * 0.2f, -s * 0.15f, s * 0.9f, -s * 0.15f, iconPaint);
        canvas.restore();
    }

    /**
     * Morph mượt mà giữa icon Camera (khi mở) và icon Menu 3 gạch (khi thanh TabBar gộp hoàn toàn vào nút bên trái).
     */
    private void drawLeftCameraOrMenuIcon(Canvas canvas, float morphToHamburger) {
        float camAlpha = 1f - clamp01(morphToHamburger * 1.4f);
        float menuAlpha = clamp01((morphToHamburger - 0.25f) / 0.75f);
        float s = AndroidUtilities.dp(9.5f);

        if (camAlpha > 0.02f) {
            int a = (int) (255 * camAlpha);
            iconPaint.setColor(Color.argb(a, 245, 250, 255));
            canvas.save();
            canvas.translate(leftCenterX, leftCenterY);
            float sc = 1f - 0.3f * morphToHamburger;
            canvas.scale(sc, sc);
            RectF camRect = new RectF(-s * 0.95f, -s * 0.62f, s * 0.95f, s * 0.75f);
            canvas.drawRoundRect(camRect, s * 0.28f, s * 0.28f, iconPaint);
            canvas.drawCircle(0, s * 0.06f, s * 0.36f, iconPaint);
            canvas.restore();
        }

        if (menuAlpha > 0.02f) {
            int a = (int) (255 * menuAlpha);
            iconPaint.setColor(Color.argb(a, 255, 255, 255));
            canvas.save();
            canvas.translate(leftCenterX, leftCenterY);
            float sc = 0.7f + 0.3f * menuAlpha;
            canvas.scale(sc, sc);
            float lineHalfW = AndroidUtilities.dp(9f);
            float gapY = AndroidUtilities.dp(5.2f);
            canvas.drawLine(-lineHalfW, -gapY, lineHalfW, -gapY, iconPaint);
            canvas.drawLine(-lineHalfW, 0, lineHalfW, 0, iconPaint);
            canvas.drawLine(-lineHalfW, gapY, lineHalfW, gapY, iconPaint);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!DXModConfig.liquidGlassEnabled) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = x;
                downY = y;
                boolean hitLeft = Math.hypot(x - leftCenterX, y - leftCenterY) <= leftRadius * 1.3f;
                boolean hitRight = collapseProgress < 0.7f && Math.hypot(x - rightCenterX, y - rightCenterY) <= rightRadius * 1.3f;
                boolean hitCenter = collapseProgress < 0.85f && centerPillRect.contains(x, y);

                if (!hitLeft && !hitRight && !hitCenter) {
                    return false;
                }

                pressSpring.getSpring().setFinalPosition(1000f);
                pressSpring.start();

                if (hitCenter) {
                    isDraggingTab = true;
                    updateTabFromTouch(x);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (isDraggingTab && collapseProgress < 0.8f) {
                    updateTabFromTouch(x);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pressSpring.getSpring().setFinalPosition(0f);
                pressSpring.start();

                float moveDist = (float) Math.hypot(x - downX, y - downY);
                boolean hitLeft = Math.hypot(downX - leftCenterX, downY - leftCenterY) <= leftRadius * 1.35f;
                boolean hitRight = collapseProgress < 0.7f && Math.hypot(downX - rightCenterX, downY - rightCenterY) <= rightRadius * 1.35f;

                if (hitLeft && moveDist < AndroidUtilities.dp(18f)) {
                    // Nếu đang gộp thành nút 3 gạch -> bấm vào nút 3 gạch sẽ bung ngược ra với hiệu ứng giọt nước
                    if (collapseProgress >= 0.65f) {
                        animateCollapseTo(0f);
                    } else if (callback != null) {
                        callback.onQuickCameraClicked();
                    }
                } else if (hitRight && moveDist < AndroidUtilities.dp(18f)) {
                    if (callback != null) {
                        callback.onNewGroupClicked();
                    }
                } else if (isDraggingTab) {
                    int targetIdx = Math.round(animatedTabIndex);
                    targetIdx = Math.max(0, Math.min(tabLabels.length - 1, targetIdx));
                    selectedTabIndex = targetIdx;
                    tabIndexSpring.getSpring().setFinalPosition(targetIdx * 1000f);
                    tabIndexSpring.start();
                    if (callback != null) {
                        callback.onTabSelected(targetIdx);
                    }
                }
                isDraggingTab = false;
                return true;
            }
        }
        return false;
    }

    private void updateTabFromTouch(float touchX) {
        float relX = (touchX - centerPillRect.left) / Math.max(1f, centerPillRect.width());
        float rawIdx = relX * tabLabels.length - 0.5f;
        rawIdx = Math.max(0f, Math.min(tabLabels.length - 1f, rawIdx));
        tabIndexSpring.getSpring().setFinalPosition(rawIdx * 1000f);
        tabIndexSpring.start();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }
}
