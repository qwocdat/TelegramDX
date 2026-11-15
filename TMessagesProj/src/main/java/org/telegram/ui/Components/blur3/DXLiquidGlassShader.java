package org.telegram.ui.Components.blur3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.SweepGradient;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.DXModConfig;

public class DXLiquidGlassShader {

    private static final String AGSL_REFRACTION_DISPERSION =
        "uniform shader img;\n" +
        "uniform float2 uSize;\n" +
        "uniform float uRadius;\n" +
        "uniform float uRefractionHeight;\n" +
        "uniform float uRefractionAmount;\n" +
        "uniform float uDispersion;\n" +
        "uniform float uVibrancy;\n" +
        "uniform float2 uLightDir;\n" +
        "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
        "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
        "    return outside + inside;\n" +
        "}\n" +
        "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
        "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
        "    } else {\n" +
        "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
        "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
        "    }\n" +
        "}\n" +
        "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n" +
        "half3 applyVibrancy(half3 c, float sat) {\n" +
        "    float luma = dot(c, half3(0.2126, 0.7152, 0.0722));\n" +
        "    return clamp(mix(half3(luma), c, sat), 0.0, 1.0);\n" +
        "}\n" +
        "half4 main(float2 coord) {\n" +
        "    float2 halfSize = uSize * 0.5;\n" +
        "    float2 centered = coord - halfSize;\n" +
        "    float r = min(uRadius, min(halfSize.x, halfSize.y));\n" +
        "    float sd = sdRoundedRect(centered, halfSize, r);\n" +
        "    if (sd > 0.0) return half4(0.0);\n" +
        "    float d = clamp(-sd / max(uRefractionHeight, 1.0), 0.0, 1.0);\n" +
        "    float2 normal = gradSdRoundedRect(centered, halfSize, r);\n" +
        "    float refractMag = (1.0 - circleMap(1.0 - d)) * uRefractionAmount;\n" +
        "    float2 baseOffset = -normal * refractMag;\n" +
        "    float disp = uDispersion * (1.0 - d);\n" +
        "    half4 cR = img.eval(coord + baseOffset * (1.0 + disp * 0.6));\n" +
        "    half4 cG = img.eval(coord + baseOffset);\n" +
        "    half4 cB = img.eval(coord + baseOffset * (1.0 - disp * 0.6));\n" +
        "    half3 rgb = applyVibrancy(half3(cR.r, cG.g, cB.b), uVibrancy);\n" +
        "    float rim = smoothstep(0.35, 0.0, d);\n" +
        "    float spec = pow(max(dot(normal, normalize(uLightDir)), 0.0), 3.0) + pow(max(dot(normal, -normalize(uLightDir)), 0.0), 4.0) * 0.65;\n" +
        "    rgb += half3(spec * rim * 0.35);\n" +
        "    float innerShadow = smoothstep(0.0, 0.25, d);\n" +
        "    rgb *= mix(0.92, 1.0, innerShadow);\n" +
        "    return half4(rgb, cG.a);\n" +
        "}\n";

    private static final String AGSL_METABALL_DROPLETS =
        "uniform float2 uRes;\n" +
        "uniform float4 uLeftRect;\n" +
        "uniform float4 uCenterRect;\n" +
        "uniform float4 uRightRect;\n" +
        "uniform float uSmoothK;\n" +
        "uniform float2 uLightDir;\n" +
        "uniform half4 uTint;\n" +
        "uniform half4 uRimColor;\n" +
        "float sdPill(float2 p, float2 c, float2 hs) {\n" +
        "    float r = min(hs.x, hs.y);\n" +
        "    float2 d = abs(p - c) - (hs - float2(r));\n" +
        "    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;\n" +
        "}\n" +
        "float smin(float a, float b, float k) {\n" +
        "    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);\n" +
        "    return mix(b, a, h) - k * h * (1.0 - h);\n" +
        "}\n" +
        "half4 main(float2 coord) {\n" +
        "    float dL = uLeftRect.z > 1.0 ? sdPill(coord, uLeftRect.xy, uLeftRect.zw) : 9999.0;\n" +
        "    float dC = uCenterRect.z > 1.0 ? sdPill(coord, uCenterRect.xy, uCenterRect.zw) : 9999.0;\n" +
        "    float dR = uRightRect.z > 1.0 ? sdPill(coord, uRightRect.xy, uRightRect.zw) : 9999.0;\n" +
        "    float d = smin(smin(dL, dC, uSmoothK), dR, uSmoothK);\n" +
        "    float aa = 1.5;\n" +
        "    float alpha = 1.0 - smoothstep(-aa, aa, d);\n" +
        "    if (alpha <= 0.001) return half4(0.0);\n" +
        "    float2 eps = float2(1.5, 0.0);\n" +
        "    float gx = smin(smin(sdPill(coord + eps.xy, uLeftRect.xy, uLeftRect.zw), sdPill(coord + eps.xy, uCenterRect.xy, uCenterRect.zw), uSmoothK), sdPill(coord + eps.xy, uRightRect.xy, uRightRect.zw), uSmoothK) - d;\n" +
        "    float gy = smin(smin(sdPill(coord + eps.yx, uLeftRect.xy, uLeftRect.zw), sdPill(coord + eps.yx, uCenterRect.xy, uCenterRect.zw), uSmoothK), sdPill(coord + eps.yx, uRightRect.xy, uRightRect.zw), uSmoothK) - d;\n" +
        "    float2 n = normalize(float2(gx, gy) + float2(0.0001));\n" +
        "    float rim = smoothstep(-5.0, -0.5, d) * alpha;\n" +
        "    float spec1 = pow(max(dot(n, normalize(uLightDir)), 0.0), 3.0);\n" +
        "    float spec2 = pow(max(dot(n, -normalize(uLightDir)), 0.0), 4.0) * 0.6;\n" +
        "    float spec = (spec1 + spec2) * rim;\n" +
        "    half3 col = mix(uTint.rgb, uRimColor.rgb, clamp(spec * 1.4 + rim * 0.25, 0.0, 1.0));\n" +
        "    float finalAlpha = clamp(uTint.a + spec * 0.55 + rim * 0.18, 0.0, 1.0) * alpha;\n" +
        "    return half4(col * finalAlpha, finalAlpha);\n" +
        "}\n";

    private static float lightDirX = 0.707f;
    private static float lightDirY = -0.707f;
    private static boolean sensorRegistered = false;
    private static final RectF internalRectF = new RectF();

    public static void ensureGravitySensor(Context context) {
        if (sensorRegistered || context == null) return;
        try {
            SensorManager sm = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                Sensor gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (gravity != null) {
                    sensorRegistered = true;
                    sm.registerListener(new SensorEventListener() {
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            float gx = event.values[0];
                            float gy = event.values[1];
                            float len = (float) Math.sqrt(gx * gx + gy * gy);
                            if (len > 0.5f) {
                                lightDirX = lightDirX * 0.85f + (-gx / len) * 0.15f;
                                lightDirY = lightDirY * 0.85f + (gy / len) * 0.15f;
                            }
                        }
                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                    }, gravity, SensorManager.SENSOR_DELAY_UI);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static float getLightAngleDeg() {
        return (float) Math.toDegrees(Math.atan2(lightDirY, lightDirX));
    }

    public static void drawGlassRim(Canvas canvas, Rect rect, float radius, boolean isDark, float alphaMul) {
        if (rect == null) return;
        internalRectF.set(rect);
        drawGlassRim(canvas, internalRectF, radius, isDark, alphaMul);
    }

    public static void drawGlassRim(Canvas canvas, RectF rect, float radius, boolean isDark, float alphaMul) {
        if (!DXModConfig.isLiquidGlassEnabled() || rect == null || rect.width() <= 2 || rect.height() <= 2) return;
        Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(AndroidUtilities.dpf2(1.15f));

        int a1 = Math.round((isDark ? 120 : 195) * alphaMul);
        int a2 = Math.round((isDark ? 24 : 45) * alphaMul);
        int a3 = Math.round((isDark ? 85 : 140) * alphaMul);
        int[] colors = new int[]{
            Color.argb(a1, 255, 255, 255),
            Color.argb(a2, 200, 230, 255),
            Color.argb(a3, 255, 255, 255),
            Color.argb(a2, 255, 220, 245),
            Color.argb(a1, 255, 255, 255)
        };
        SweepGradient sg = new SweepGradient(rect.centerX(), rect.centerY(), colors, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f});
        Matrix m = new Matrix();
        m.setRotate(getLightAngleDeg(), rect.centerX(), rect.centerY());
        sg.setLocalMatrix(m);
        rimPaint.setShader(sg);
        canvas.drawRoundRect(rect, radius, radius, rimPaint);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static RenderEffect createRefractionEffect(float w, float h, float radiusPx) {
        if (!DXModConfig.isLiquidGlassEnabled() || w <= 2 || h <= 2) return null;
        try {
            RuntimeShader shader = new RuntimeShader(AGSL_REFRACTION_DISPERSION);
            shader.setFloatUniform("uSize", w, h);
            shader.setFloatUniform("uRadius", radiusPx);
            shader.setFloatUniform("uRefractionHeight", AndroidUtilities.dpf2(14f));
            shader.setFloatUniform("uRefractionAmount", AndroidUtilities.dpf2(10f));
            shader.setFloatUniform("uDispersion", 0.14f);
            shader.setFloatUniform("uVibrancy", 1.5f);
            shader.setFloatUniform("uLightDir", lightDirX, lightDirY);
            return RenderEffect.createRuntimeShaderEffect(shader, "img");
        } catch (Throwable ignore) {
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static class MetaballRenderer {
        private RuntimeShader shader;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public void draw(Canvas canvas, float w, float h,
                         float lCx, float lCy, float lHw, float lHh,
                         float cCx, float cCy, float cHw, float cHh,
                         float rCx, float rCy, float rHw, float rHh,
                         float smoothK, boolean isDark) {
            if (!DXModConfig.isLiquidGlassEnabled()) return;
            try {
                if (shader == null) {
                    shader = new RuntimeShader(AGSL_METABALL_DROPLETS);
                }
                shader.setFloatUniform("uRes", w, h);
                shader.setFloatUniform("uLeftRect", lCx, lCy, lHw, lHh);
                shader.setFloatUniform("uCenterRect", cCx, cCy, cHw, cHh);
                shader.setFloatUniform("uRightRect", rCx, rCy, rHw, rHh);
                shader.setFloatUniform("uSmoothK", smoothK);
                shader.setFloatUniform("uLightDir", lightDirX, lightDirY);
                if (isDark) {
                    shader.setFloatUniform("uTint", 0.14f, 0.16f, 0.22f, 0.42f);
                    shader.setFloatUniform("uRimColor", 0.85f, 0.92f, 1.0f, 0.85f);
                } else {
                    shader.setFloatUniform("uTint", 0.96f, 0.98f, 0.99f, 0.48f);
                    shader.setFloatUniform("uRimColor", 1.0f, 1.0f, 1.0f, 0.95f);
                }
                paint.setShader(shader);
                canvas.drawRect(0, 0, w, h, paint);
            } catch (Throwable ignore) {
            }
        }
    }

    public static void attachSpringPress(final View view) {
        if (view == null) return;
        final SpringAnimation sx = new SpringAnimation(view, DynamicAnimation.SCALE_X, 1f);
        final SpringAnimation sy = new SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f);
        sx.setSpring(new SpringForce(1f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.42f));
        sy.setSpring(new SpringForce(1f).setStiffness(SpringForce.STIFFNESS_MEDIUM).setDampingRatio(0.42f));
        view.setOnTouchListener((v, event) -> {
            if (!DXModConfig.isLiquidGlassEnabled()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                sx.animateToFinalPosition(0.94f);
                sy.animateToFinalPosition(0.94f);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                sx.animateToFinalPosition(1f);
                sy.animateToFinalPosition(1f);
            }
            return false;
        });
    }
}
