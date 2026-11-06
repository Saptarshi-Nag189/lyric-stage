package com.lyricstage.background

/**
 * The liquid background, redrawn the way Apple draws a background: a soft colour wash in
 * the artwork's own colours - as smooth as a blur, with no visible edge anywhere - slowly
 * drifting, with a fine ripple in its own colour from each sung word and a soft band of
 * light across the glass when a new line begins.
 *
 * What is gone is everything that read as "tricks": no undulating slab, no refraction, no
 * dispersion fringes at the edges of the light, no specular glints, no saturation push.
 * The colour is exactly what the cover has - nothing is pushed off its luminance - so a
 * grey cover stays grey with no special case, and the wash can never blow a light cover
 * out to white or crush a dark one to black.
 *
 * Coordinates: fragCoord has a top-left origin; p is centred, y down, aspect corrected.
 *
 * Every stage colour stays bounded: the wash mixes toward the ripple colours instead of
 * adding light onto the base, and nothing reaches the filmic tail below zero - pow() of a
 * negative is NaN, and a NaN pixel is garbage on some GPUs. The tail itself stays: it is
 * the tone map the light lyric shade is measured against (Colour.rolled), and it keeps
 * the brightest spots well below the type.
 */
internal const val LIQUID_GLASS_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform half3 uC0;
uniform half3 uC1;
uniform half3 uBase;
uniform float4 uRip[8];
uniform float uLineT;
uniform float uPools;
uniform float uRipple;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 1.9 + float2(1.7, 9.2);
        a *= 0.45;
    }
    return v / 0.88;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    float aspect = uRes.x / uRes.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float t = uTime;

    // the colour wash: the artwork's own colours in a drift as soft as a blur. The domain
    // is wide, so each field spans most of the screen and no edge of it is visible; the
    // two fields are the ripple colours, the same colours the rings carry, so the wash and
    // the waves agree. uPools scales how far each mix reaches: 0 nearly flat base, 1
    // standard, 2 vivid. A grey cover stays grey: mixing grey toward grey changes nothing.
    float2 s = p * 0.34;
    float2 q = float2(fbm(s + float2(0.0, t * 0.016)), fbm(s + float2(4.7, -t * 0.013)));
    float2 r = float2(fbm(s + 1.2 * q + float2(1.7, 9.2) + t * 0.010),
                      fbm(s + 1.2 * q + float2(8.3, 2.8) - t * 0.012));
    float f = fbm(s * 0.8 + 1.2 * r);
    half pools = half(min(uPools, 2.0));
    half3 col = uBase;
    col = mix(col, uC0, half(min(smoothstep(0.30, 0.85, f) * 0.45 * pools, 0.55)));
    col = mix(col, uC1, half(min(smoothstep(0.52, 0.94, q.y) * 0.28 * pools, 0.35)));

    // ripples: a damped ring travelling outwards from each sung word, in its own colour -
    // fine and light on dark glass, fine and darker on light glass, so the same ring reads
    // on a black cover exactly as it does on a purple one
    float ring = 0.0;
    float glow = 0.0;
    for (int i = 0; i < 8; i++) {
        float4 rp = uRip[i];
        float age = t - rp.z;
        if (rp.w > 0.0 && age >= 0.0 && age < 3.5) {
            float2 c = float2((rp.x - 0.5) * aspect, rp.y - 0.5);
            float2 d = p - c;
            float dist = length(d);
            float x = dist - age * 0.32;
            float env = exp(-x * x * 90.0) * exp(-age * 1.25) * rp.w;
            ring += env * uRipple;
            // a wide, soft version of the same ring, for dark glass
            glow += exp(-x * x * 14.0) * exp(-age * 1.4) * rp.w * uRipple;
        }
    }
    half lum0 = dot(col, half3(0.299, 0.587, 0.114));
    half dark = 1.0 - smoothstep(0.04, 0.3, lum0);
    half rw = half(min(ring * 0.55 * (1.0 - dark) + min(glow, 1.5) * 0.30 * dark, 1.0));
    col = mix(col, uC0, rw);

    // a soft band of light across the glass on each new line
    float lineAge = t - uLineT;
    if (lineAge > 0.0 && lineAge < 2.2) {
        float sw = (p.x * 0.7 - p.y * 0.45) - (lineAge * 1.5 - 1.2);
        col += half(exp(-sw * sw * 16.0) * (1.0 - lineAge / 2.2) * 0.10);
    }

    // nothing negative survives to the tone map
    col = max(col, half3(0.0));

    // vignette, then the filmic roll-off that keeps highlights under the white type
    col *= half(1.0 - 0.6 * smoothstep(0.3, 1.2, length(p * float2(0.85, 1.1))));
    col = col / (1.0 + col * 0.7);
    col = pow(col, half3(1.18)) * 0.95;
    col += half((hash(fragCoord + fract(t) * 100.0) - 0.5) * 0.018);
    return half4(col, 1.0);
}
"""
