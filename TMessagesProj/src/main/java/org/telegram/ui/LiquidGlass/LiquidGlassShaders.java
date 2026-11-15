package org.telegram.ui.LiquidGlass;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;
import androidx.annotation.RequiresApi;

/**
 * Chuyển đổi nguyên bản 100% AGSL Shaders từ liquid.zip (Lens.kt, Vibrancy.kt, InnerShadow.kt)
 * kết hợp Smooth-Minimum Polynomial SDF (smin) tạo hiệu ứng 2 giọt nước chạm và hòa vào nhau (Gooey Metaball).
 */
public final class LiquidGlassShaders {

    public static final String ROUNDED_RECT_SDF =
        "float radiusAt(float2 coord, float4 radii) {\n" +
        "    if (coord.x >= 0.0) {\n" +
        "        if (coord.y <= 0.0) return radii.y;\n" +
        "        else return radii.z;\n" +
        "    } else {\n" +
        "        if (coord.y <= 0.0) return radii.x;\n" +
        "        else return radii.w;\n" +
        "    }\n" +
        "}\n" +
        "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
        "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
        "    return outside + inside;\n" +
        "}\n" +
        "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
        "        float2 m = max(cornerCoord, 0.0);\n" +
        "        float l = length(m);\n" +
        "        return l > 0.0001 ? sign(coord) * (m / l) : float2(0.0, 1.0);\n" +
        "    } else {\n" +
        "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
        "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
        "    }\n" +
        "}\n" +
        "float smin(float a, float b, float k) {\n" +
        "    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.0001), 0.0, 1.0);\n" +
        "    return mix(b, a, h) - k * h * (1.0 - h);\n" +
        "}\n";

    /**
     * Shader khúc xạ thấu kính + tán sắc 7 dải màu cầu vồng (Chromatic Dispersion) + Specular Highlight
     * Dành cho mọi nút bấm (Button), thanh trượt (Slider) và ô nhập văn bản (EditText) như trong video 19s.
     */
    public static final String LENS_DISPERSION_WIDGET_AGSL =
        "uniform shader content;\n" +
        "uniform float2 size;\n" +
        "uniform float2 offset;\n" +
        "uniform float4 cornerRadii;\n" +
        "uniform float refractionHeight;\n" +
        "uniform float refractionAmount;\n" +
        "uniform float depthEffect;\n" +
        "uniform float chromaticAberration;\n" +
        "uniform float2 lightDir;\n" +
        "uniform float pressProgress;\n" +
        "uniform float4 surfaceTint;\n" +
        ROUNDED_RECT_SDF +
        "float circleMap(float x) {\n" +
        "    float cx = clamp(x, 0.0, 1.0);\n" +
        "    return 1.0 - sqrt(max(0.0, 1.0 - cx * cx));\n" +
        "}\n" +
        "half3 applyVibrancy(half3 c, float sat) {\n" +
        "    float luma = dot(c, half3(0.2126, 0.7152, 0.0722));\n" +
        "    return clamp(mix(half3(luma), c, sat), 0.0, 1.0);\n" +
        "}\n" +
        "half4 main(float2 coord) {\n" +
        "    float2 halfSize = size * 0.5;\n" +
        "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
        "    float radius = radiusAt(centeredCoord, cornerRadii);\n" +
        "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
        "    if (sd > 0.5) {\n" +
        "        return content.eval(coord);\n" +
        "    }\n" +
        "    float edgeAlpha = clamp(0.5 - sd, 0.0, 1.0);\n" +
        "    float clampedSd = min(sd, 0.0);\n" +
        "    float d = 0.0;\n" +
        "    if (-clampedSd < refractionHeight && refractionHeight > 0.0) {\n" +
        "        d = circleMap(1.0 - (-clampedSd / refractionHeight)) * refractionAmount;\n" +
        "    }\n" +
        "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
        "    float2 baseGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n" +
        "    float2 normCenter = length(centeredCoord) > 0.001 ? normalize(centeredCoord) : float2(0.0);\n" +
        "    float2 grad = normalize(baseGrad + depthEffect * normCenter + float2(0.0001));\n" +
        "    float2 refractedCoord = coord + d * grad;\n" +
        "    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(1.0, halfSize.x * halfSize.y));\n" +
        "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
        "    half4 color = half4(0.0);\n" +
        "    half4 red = content.eval(refractedCoord + dispersedCoord);\n" +
        "    color.r += red.r / 3.5; color.a += red.a / 7.0;\n" +
        "    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n" +
        "    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;\n" +
        "    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n" +
        "    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;\n" +
        "    half4 green = content.eval(refractedCoord);\n" +
        "    color.g += green.g / 3.5; color.a += green.a / 7.0;\n" +
        "    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n" +
        "    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;\n" +
        "    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n" +
        "    color.b += blue.b / 3.0; color.a += blue.a / 7.0;\n" +
        "    half4 purple = content.eval(refractedCoord - dispersedCoord);\n" +
        "    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;\n" +
        "    color.rgb = applyVibrancy(color.rgb, 1.45);\n" +
        "    color.rgb = mix(color.rgb, half3(surfaceTint.rgb), surfaceTint.a);\n" +
        "    float rimBand = exp(-max(0.0, -sd) * 0.28);\n" +
        "    float spec1 = pow(max(0.0, dot(grad, lightDir)), 3.0);\n" +
        "    float spec2 = pow(max(0.0, dot(grad, -lightDir)), 4.0) * 0.45;\n" +
        "    float innerShadow = smoothstep(0.0, 18.0, -sd) * (1.0 - smoothstep(18.0, 26.0, -sd)) * 0.12 * (1.0 + pressProgress);\n" +
        "    color.rgb -= half3(innerShadow);\n" +
        "    color.rgb += half3((0.18 + 0.55 * (spec1 + spec2) + 0.18 * pressProgress) * rimBand);\n" +
        "    half4 orig = content.eval(coord);\n" +
        "    return mix(orig, half4(clamp(color.rgb, 0.0, 1.0), 1.0), edgeAlpha);\n" +
        "}\n";

    /**
     * Shader Metaball Gooey Liquid Glass cho thanh TabBar + Nút Camera (Trái) + Nút Tạo Nhóm (Phải).
     * Khi cuộn xuống, trường khoảng cách SDF của 3 khối hòa quyện như giọt nước chạm nhau (smin)
     * với khúc xạ thấu kính, tán sắc Chromatic Aberration và viền sáng Specular liền mạch.
     */
    public static final String TABBAR_METABALL_LIQUID_AGSL =
        "uniform shader content;\n" +
        "uniform float2 resolution;\n" +
        "uniform float4 centerPill;\n" +   // xy = center, zw = halfSize
        "uniform float3 leftDrop;\n" +     // xy = center, z = radius
        "uniform float3 rightDrop;\n" +    // xy = center, z = radius
        "uniform float2 indicatorPos;\n" + // x = centerX, y = halfWidth
        "uniform float gooeyK;\n" +        // Độ nhớt giọt nước (22dp..34dp)
        "uniform float refractionHeight;\n" +
        "uniform float refractionAmount;\n" +
        "uniform float chromaticAberration;\n" +
        "uniform float2 lightDir;\n" +
        "uniform float pressProgress;\n" +
        "uniform float rightMergeAlpha;\n" +
        "uniform float leftMergeAlpha;\n" +
        "uniform float4 surfaceTint;\n" +
        ROUNDED_RECT_SDF +
        "float circleMap(float x) {\n" +
        "    float cx = clamp(x, 0.0, 1.0);\n" +
        "    return 1.0 - sqrt(max(0.0, 1.0 - cx * cx));\n" +
        "}\n" +
        "float sceneSDF(float2 p) {\n" +
        "    float rMain = min(centerPill.z, centerPill.w);\n" +
        "    float dMain = sdRoundedRect(p - centerPill.xy, centerPill.zw, rMain);\n" +
        "    float dLeft = length(p - leftDrop.xy) - leftDrop.z;\n" +
        "    float dRight = length(p - rightDrop.xy) - rightDrop.z;\n" +
        "    float d = dMain;\n" +
        "    if (rightMergeAlpha > 0.01) {\n" +
        "        d = smin(d, dRight, gooeyK);\n" +
        "    }\n" +
        "    if (leftMergeAlpha > 0.01) {\n" +
        "        d = smin(d, dLeft, gooeyK);\n" +
        "    }\n" +
        "    return d;\n" +
        "}\n" +
        "float2 sceneGrad(float2 p) {\n" +
        "    float eps = 1.5;\n" +
        "    float dx = sceneSDF(p + float2(eps, 0.0)) - sceneSDF(p - float2(eps, 0.0));\n" +
        "    float dy = sceneSDF(p + float2(0.0, eps)) - sceneSDF(p - float2(0.0, eps));\n" +
        "    float2 g = float2(dx, dy);\n" +
        "    float l = length(g);\n" +
        "    return l > 0.0001 ? (g / l) : float2(0.0, -1.0);\n" +
        "}\n" +
        "half3 applyVibrancy(half3 c, float sat) {\n" +
        "    float luma = dot(c, half3(0.2126, 0.7152, 0.0722));\n" +
        "    return clamp(mix(half3(luma), c, sat), 0.0, 1.0);\n" +
        "}\n" +
        "half4 main(float2 coord) {\n" +
        "    float sd = sceneSDF(coord);\n" +
        "    if (sd > 1.0) {\n" +
        "        return half4(0.0);\n" +
        "    }\n" +
        "    float mask = clamp(0.6 - sd, 0.0, 1.0);\n" +
        "    float clampedSd = min(sd, 0.0);\n" +
        "    float d = 0.0;\n" +
        "    if (-clampedSd < refractionHeight && refractionHeight > 0.0) {\n" +
        "        d = -circleMap(1.0 - (-clampedSd / refractionHeight)) * refractionAmount;\n" +
        "    }\n" +
        "    float2 grad = sceneGrad(coord);\n" +
        "    float2 refractedCoord = clamp(coord + d * grad, float2(1.0), resolution - float2(1.0));\n" +
        "    float2 rel = (coord - centerPill.xy) / max(float2(1.0), centerPill.zw);\n" +
        "    float dispersionIntensity = chromaticAberration * clamp(rel.x * rel.y, -1.0, 1.0);\n" +
        "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
        "    half4 color = half4(0.0);\n" +
        "    half4 red = content.eval(refractedCoord + dispersedCoord);\n" +
        "    color.r += red.r / 3.5; color.a += red.a / 7.0;\n" +
        "    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n" +
        "    color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;\n" +
        "    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n" +
        "    color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;\n" +
        "    half4 green = content.eval(refractedCoord);\n" +
        "    color.g += green.g / 3.5; color.a += green.a / 7.0;\n" +
        "    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n" +
        "    color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;\n" +
        "    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n" +
        "    color.b += blue.b / 3.0; color.a += blue.a / 7.0;\n" +
        "    half4 purple = content.eval(refractedCoord - dispersedCoord);\n" +
        "    color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;\n" +
        "    color.rgb = applyVibrancy(color.rgb, 1.5);\n" +
        "    color.rgb = mix(color.rgb, half3(surfaceTint.rgb), surfaceTint.a);\n" +
        "    float2 indHalf = float2(indicatorPos.y, max(8.0, centerPill.w - 6.0));\n" +
        "    float dInd = sdRoundedRect(coord - float2(indicatorPos.x, centerPill.y), indHalf, min(indHalf.x, indHalf.y));\n" +
        "    if (dInd < 0.0 && leftMergeAlpha > 0.15) {\n" +
        "        float indRim = exp(-max(0.0, -dInd) * 0.25);\n" +
        "        color.rgb += half3(0.11 + 0.14 * pressProgress + 0.22 * indRim);\n" +
        "    }\n" +
        "    float rim = exp(-max(0.0, -sd) * 0.26);\n" +
        "    float specPrimary = pow(max(0.0, dot(grad, lightDir)), 3.2);\n" +
        "    float specSecondary = pow(max(0.0, dot(grad, -lightDir)), 4.2) * 0.4;\n" +
        "    color.rgb += half3((0.16 + 0.62 * (specPrimary + specSecondary)) * rim);\n" +
        "    return half4(clamp(color.rgb, 0.0, 1.0) * mask, mask);\n" +
        "}\n";

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static RuntimeShader createWidgetLensShader() {
        return new RuntimeShader(LENS_DISPERSION_WIDGET_AGSL);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static RuntimeShader createTabBarMetaballShader() {
        return new RuntimeShader(TABBAR_METABALL_LIQUID_AGSL);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static RenderEffect buildWidgetRenderEffect(
            RuntimeShader shader,
            float width,
            float height,
            float cornerRadius,
            float refractionHeightPx,
            float refractionAmountPx,
            float chromaticAberration,
            float lightDirX,
            float lightDirY,
            float pressProgress,
            boolean isDark) {
        shader.setFloatUniform("size", Math.max(1f, width), Math.max(1f, height));
        shader.setFloatUniform("offset", 0f, 0f);
        float maxR = Math.min(width, height) * 0.5f;
        float r = Math.min(cornerRadius, maxR);
        shader.setFloatUniform("cornerRadii", r, r, r, r);
        shader.setFloatUniform("refractionHeight", refractionHeightPx);
        shader.setFloatUniform("refractionAmount", -refractionAmountPx);
        shader.setFloatUniform("depthEffect", 1.0f);
        shader.setFloatUniform("chromaticAberration", chromaticAberration);
        shader.setFloatUniform("lightDir", lightDirX, lightDirY);
        shader.setFloatUniform("pressProgress", pressProgress);
        if (isDark) {
            shader.setFloatUniform("surfaceTint", 0.10f, 0.13f, 0.18f, 0.28f);
        } else {
            shader.setFloatUniform("surfaceTint", 0.95f, 0.97f, 1.0f, 0.24f);
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content");
    }
}
