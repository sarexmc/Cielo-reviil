package com.dedsafio;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Random;

@Mod.EventBusSubscriber(modid = DedsafioMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    private static boolean skyOn = false;
    private static int mode = 0; // 0 nada, 1 vertical, 2 horizontal
    private static double cx, cy, cz;
    private static float fx = 0, fz = 1;
    private static int ticks = 0;
    private static ModelPart revil;
    private static float[] sky;
    private static final ResourceLocation REVIL_TEX = new ResourceLocation(DedsafioMod.MODID, "textures/entity/revil.png");

    private static final float A = 34f;      // mitad del largo de la grieta
    private static final float BMAX = 10f;   // mitad del ancho maximo
    private static final float DEPTH = 10f;  // profundidad del tunel
    private static final float INNER = 0.55f;
    private static final int N = 44;

    private static float[] starS, starF, starZ, starP;

    public static void apply(DedPacket p) {
        if (p.action == DedPacket.SKY_ON) skyOn = true;
        else if (p.action == DedPacket.SKY_OFF) skyOn = false;
        else if (p.action == DedPacket.START_V || p.action == DedPacket.START_H) {
            mode = p.action == DedPacket.START_V ? 1 : 2;
            cx = p.x; cy = p.y; cz = p.z; fx = p.fx; fz = p.fz; ticks = 0;
            if (fx == 0 && fz == 0) fz = 1;
        } else if (p.action == DedPacket.STOP) mode = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && mode != 0 && !Minecraft.getInstance().isPaused()) ticks++;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor e) {
        if (skyOn) { e.setRed(0.42f); e.setGreen(0.02f); e.setBlue(0.05f); }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY && skyOn) drawSky(e);
        else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && mode != 0) drawCrack(e);
    }

    // ---------- utilidades ----------
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp01(float t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }
    private static float sm(float t) { return t * t * (3 - 2 * t); }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFF) / 65535f;
    }

    private static float noise(float x, float y, float z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float xf = sm(x - xi), yf = sm(y - yi), zf = sm(z - zi);
        float c00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), xf);
        float c10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), xf);
        float c01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), xf);
        float c11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), xf);
        return lerp(lerp(c00, c10, yf), lerp(c01, c11, yf), zf);
    }

    private static float fbm(float x, float y, float z) {
        float s = 0, a = 0.5f;
        for (int i = 0; i < 4; i++) { s += a * noise(x, y, z); x *= 2.03f; y *= 2.03f; z *= 2.03f; a *= 0.5f; }
        return s / 0.9375f;
    }

    private static float noise1(float x) { return noise(x, 7.3f, 1.9f); }

    // ---------- cielo rojo ----------
    private static void buildSky() {
        int G = 24; float R = 100f;
        sky = new float[6 * G * G * 4 * 6];
        int k = 0;
        for (int face = 0; face < 6; face++)
            for (int i = 0; i < G; i++)
                for (int j = 0; j < G; j++)
                    for (int c = 0; c < 4; c++) {
                        int ii = i + ((c == 1 || c == 2) ? 1 : 0);
                        int jj = j + (c >= 2 ? 1 : 0);
                        float a = -1f + 2f * ii / G, b = -1f + 2f * jj / G;
                        float x, y, z;
                        if (face == 0) { x = 1; y = a; z = b; }
                        else if (face == 1) { x = -1; y = a; z = b; }
                        else if (face == 2) { x = a; y = 1; z = b; }
                        else if (face == 3) { x = a; y = -1; z = b; }
                        else if (face == 4) { x = a; y = b; z = 1; }
                        else { x = a; y = b; z = -1; }
                        float len = (float) Math.sqrt(x * x + y * y + z * z);
                        x /= len; y /= len; z /= len;
                        float n = fbm(x * 3f + 5f, y * 3f, z * 3f);
                        float hor = 1f - Math.abs(y);
                        float r = 0.07f + 0.85f * (float) Math.pow(n, 1.4) * (0.55f + 0.45f * hor);
                        float g = 0.005f + 0.03f * n;
                        float bl = 0.03f + 0.12f * n * hor;
                        if (y < 0) { r *= 0.6f; bl *= 0.6f; }
                        sky[k++] = x * R; sky[k++] = y * R; sky[k++] = z * R;
                        sky[k++] = r; sky[k++] = g; sky[k++] = bl;
                    }
    }

    private static void drawSky(RenderLevelStageEvent e) {
        if (sky == null) buildSky();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        long gt = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        ps.mulPose(Axis.YP.rotationDegrees((gt + e.getPartialTick()) * 0.04f));
        Matrix4f m = ps.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < sky.length; i += 6)
            b.vertex(m, sky[i], sky[i + 1], sky[i + 2]).color(sky[i + 3], sky[i + 4], sky[i + 5], 1f).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        ps.popPose();
    }

    // ---------- grieta ----------
    private static double camX, camY, camZ, rx, rz, bx, bz;
    private static boolean vertical;

    private static float wob(float s) { return (noise1(s * 5f + 11f) - 0.5f) * 5f; }

    private static float jag(float s, float seed) {
        return 0.55f + 0.9f * noise1(s * 14f + seed) * (0.75f + 0.5f * noise1(s * 70f + seed));
    }

    private static float halfW(float s, float open, float seed) {
        float base = Math.max(0f, 1f - s * s);
        return BMAX * open * (float) Math.pow(base, 1.2) * jag(s, seed);
    }

    private static void pt(BufferBuilder b, Matrix4f m, float s, float q, float w, float r, float g, float bl, float a) {
        float u = vertical ? q : s;
        float v = vertical ? s : q;
        float x = (float) (cx + rx * u + bx * w - camX);
        float y = (float) (cy + v - camY);
        float z = (float) (cz + rz * u + bz * w - camZ);
        b.vertex(m, x, y, z).color(r, g, bl, a).endVertex();
    }

    private static void beginQuads(BufferBuilder b) {
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void strip(BufferBuilder b, Matrix4f m, float s0, float s1, float qa0, float qa1, float qb0, float qb1,
                              float w0, float w1, float[] c0, float[] c1) {
        // cuadrilatero entre el borde "a" (w0) y el borde "b" (w1) a lo largo de [s0,s1]
        pt(b, m, s0, qa0, w0, c0[0], c0[1], c0[2], c0[3]);
        pt(b, m, s1, qa1, w0, c0[0], c0[1], c0[2], c0[3]);
        pt(b, m, s1 * (w1 == w0 ? 1f : INNER) , qb1, w1, c1[0], c1[1], c1[2], c1[3]);
        pt(b, m, s0 * (w1 == w0 ? 1f : INNER), qb0, w1, c1[0], c1[1], c1[2], c1[3]);
    }

    private static void thickLine(BufferBuilder b, Matrix4f m, float s0, float q0, float s1, float q1, float th,
                                  float w, float r, float g, float bl, float a) {
        float ds = s1 - s0, dq = q1 - q0;
        float len = (float) Math.sqrt(ds * ds + dq * dq);
        if (len < 1e-4f) { ds = 1; dq = 0; len = 1; }
        float ns = -dq / len * th, nq = ds / len * th;
        pt(b, m, s0 - ns, q0 - nq, w, r, g, bl, a);
        pt(b, m, s0 + ns, q0 + nq, w, r, g, bl, a);
        pt(b, m, s1 + ns, q1 + nq, w, r, g, bl, a);
        pt(b, m, s1 - ns, q1 - nq, w, r, g, bl, a);
    }

    private static void drawCrack(RenderLevelStageEvent e) {
        float t = ticks + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        camX = cam.x; camY = cam.y; camZ = cam.z;
        vertical = mode == 1;
        double fl = Math.sqrt(fx * fx + fz * fz);
        double ffx = fx / fl, ffz = fz / fl;
        rx = -ffz; rz = ffx; bx = -ffx; bz = -ffz;

        float extent = sm(clamp01(t / 60f));
        float open = sm(clamp01((t - 60f) / 80f));
        if (extent < 0.01f) return;

        float[] S = new float[N + 1], QU = new float[N + 1], QL = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            float s = extent * (-1f + 2f * i / N);
            float c = wob(s);
            S[i] = s * A;
            QU[i] = c + halfW(s, open, 3.1f);
            QL[i] = c - halfW(s, open, 9.7f);
        }

        Matrix4f m = e.getPoseStack().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().getBuilder();

        if (open > 0.001f) {
            // 1) fondo de espacio
            beginQuads(b);
            int M = 4;
            for (int i = 0; i < N; i++)
                for (int j = 0; j < M; j++) {
                    float f0 = (float) j / M, f1 = (float) (j + 1) / M;
                    float[] col0 = spaceCol(S[i] * INNER, lerp(QL[i], QU[i], f0) * INNER, f0);
                    float[] col1 = spaceCol(S[i + 1] * INNER, lerp(QL[i + 1], QU[i + 1], f0) * INNER, f0);
                    float[] col2 = spaceCol(S[i + 1] * INNER, lerp(QL[i + 1], QU[i + 1], f1) * INNER, f1);
                    float[] col3 = spaceCol(S[i] * INNER, lerp(QL[i], QU[i], f1) * INNER, f1);
                    pt(b, m, S[i] * INNER, lerp(QL[i], QU[i], f0) * INNER, -DEPTH, col0[0], col0[1], col0[2], 1f);
                    pt(b, m, S[i + 1] * INNER, lerp(QL[i + 1], QU[i + 1], f0) * INNER, -DEPTH, col1[0], col1[1], col1[2], 1f);
                    pt(b, m, S[i + 1] * INNER, lerp(QL[i + 1], QU[i + 1], f1) * INNER, -DEPTH, col2[0], col2[1], col2[2], 1f);
                    pt(b, m, S[i] * INNER, lerp(QL[i], QU[i], f1) * INNER, -DEPTH, col3[0], col3[1], col3[2], 1f);
                }
            BufferUploader.drawWithShader(b.end());

            // 2) paredes del tunel
            beginQuads(b);
            float[] outer = {0.40f, 0.0f, 0.05f, 1f}, inner = {0.04f, 0.0f, 0.10f, 1f};
            for (int i = 0; i < N; i++) {
                float n0 = noise1(S[i] * 0.4f), n1 = noise1(S[i + 1] * 0.4f);
                float[] o0 = {outer[0] * (0.6f + 0.8f * n0), 0f, 0.05f, 1f};
                float[] o1 = {outer[0] * (0.6f + 0.8f * n1), 0f, 0.05f, 1f};
                strip(b, m, S[i], S[i + 1], QU[i], QU[i + 1], QU[i] * INNER, QU[i + 1] * INNER, 0f, -DEPTH, o0, inner);
                strip(b, m, S[i], S[i + 1], QL[i], QL[i + 1], QL[i] * INNER, QL[i + 1] * INNER, 0f, -DEPTH, o1, inner);
            }
            BufferUploader.drawWithShader(b.end());

            // 3) estrellas
            if (starS == null) {
                Random rnd = new Random(1234);
                int NS = 160;
                starS = new float[NS]; starF = new float[NS]; starZ = new float[NS]; starP = new float[NS];
                for (int i = 0; i < NS; i++) {
                    starS[i] = (rnd.nextFloat() * 2f - 1f) * 0.85f;
                    starF[i] = 0.1f + rnd.nextFloat() * 0.8f;
                    starZ[i] = 0.12f + rnd.nextFloat() * 0.22f;
                    starP[i] = rnd.nextFloat() * 6.28f;
                }
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            beginQuads(b);
            for (int i = 0; i < starS.length; i++) {
                float s = starS[i] * extent;
                float c = wob(s);
                float qu = c + halfW(s, open, 3.1f), ql = c - halfW(s, open, 9.7f);
                float q = lerp(ql, qu, starF[i]) * INNER;
                float sx = s * A * INNER, z = starZ[i];
                float al = 0.55f + 0.45f * (float) Math.sin(t * 0.15f + starP[i]);
                pt(b, m, sx - z, q - z, -DEPTH + 0.05f, 1f, 0.9f, 1f, al);
                pt(b, m, sx + z, q - z, -DEPTH + 0.05f, 1f, 0.9f, 1f, al);
                pt(b, m, sx + z, q + z, -DEPTH + 0.05f, 1f, 0.9f, 1f, al);
                pt(b, m, sx - z, q + z, -DEPTH + 0.05f, 1f, 0.9f, 1f, al);
            }
            BufferUploader.drawWithShader(b.end());
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }

        // 4) Revil
        if (t > 110f) drawRevil(e, t);

        // 5) onda expansiva (solo visual) y borde brillante, aditivos
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        if (t > 150f) {
            float tc = (t - 150f) % 160f;
            if (tc >= 100f) {
                float wt = (tc - 100f) / 60f;
                float rad = 2f + wt * 30f, al = (1f - wt) * 0.85f;
                float q0 = wob(0f) * 0.8f;
                beginQuads(b);
                int SEG = 56;
                for (int i = 0; i < SEG; i++) {
                    float a0 = (float) (2 * Math.PI * i / SEG), a1 = (float) (2 * Math.PI * (i + 1) / SEG);
                    for (int layer = 0; layer < 2; layer++) {
                        float th = layer == 0 ? 2.4f : 0.7f;
                        float la = layer == 0 ? al * 0.35f : al;
                        float g = layer == 0 ? 0.05f : 0.4f;
                        float rin = rad - th, rout = rad + th;
                        pt(b, m, rin * (float) Math.cos(a0), q0 + rin * (float) Math.sin(a0), 1f, 1f, g, 0.12f, la);
                        pt(b, m, rout * (float) Math.cos(a0), q0 + rout * (float) Math.sin(a0), 1f, 1f, g, 0.12f, 0f);
                        pt(b, m, rout * (float) Math.cos(a1), q0 + rout * (float) Math.sin(a1), 1f, 1f, g, 0.12f, 0f);
                        pt(b, m, rin * (float) Math.cos(a1), q0 + rin * (float) Math.sin(a1), 1f, 1f, g, 0.12f, la);
                    }
                }
                BufferUploader.drawWithShader(b.end());
            }
        }
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.3f);
        beginQuads(b);
        for (int i = 0; i < N; i++) {
            thickLine(b, m, S[i], QU[i], S[i + 1], QU[i + 1], 0.9f, 0f, 1f, 0.05f, 0.1f, 0.30f * pulse);
            thickLine(b, m, S[i], QL[i], S[i + 1], QL[i + 1], 0.9f, 0f, 1f, 0.05f, 0.1f, 0.30f * pulse);
            thickLine(b, m, S[i], QU[i], S[i + 1], QU[i + 1], 0.22f, 0f, 1f, 0.55f, 0.65f, 0.95f);
            thickLine(b, m, S[i], QL[i], S[i + 1], QL[i + 1], 0.22f, 0f, 1f, 0.55f, 0.65f, 0.95f);
        }
        BufferUploader.drawWithShader(b.end());

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static float[] spaceCol(float s, float q, float f) {
        float n = fbm(s * 0.12f, q * 0.12f, 3.7f);
        float edge = 1f - 0.55f * Math.abs(2f * f - 1f);
        return new float[]{(0.03f + 0.45f * n * n) * edge, 0.05f * n * edge, (0.08f + 0.75f * n) * edge};
    }

    private static void drawRevil(RenderLevelStageEvent e, float t) {
        if (revil == null) revil = RevilModel.createLayer().bakeRoot();
        float appear = sm(clamp01((t - 110f) / 40f));
        float tc = Math.max(0f, t - 150f) % 160f;
        float bob = tc < 100f ? (float) Math.sin(2 * Math.PI * tc / 50f) * 0.5f : 0f;
        float pulse = 1f + (tc < 100f ? 0.02f * (float) Math.sin(2 * Math.PI * tc / 50f) : 0f);
        float q0 = wob(0f) * 0.8f;
        float u = vertical ? q0 : 0f, v = vertical ? 0f : q0, w = -4.5f;
        double wx = cx + rx * u + bx * w, wy = cy + v, wz = cz + rz * u + bz * w;

        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(wx - camX, wy - camY + bob, wz - camZ);
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(ffx(), ffz())));
        float k = RevilModel.K * appear * pulse;
        ps.scale(-k, -k, k);
        ps.translate(-RevilModel.CX / 16f, -RevilModel.CY / 16f, -RevilModel.CZ / 16f);
        MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType rt = RenderType.entityCutoutNoCull(REVIL_TEX);
        VertexConsumer vc = bs.getBuffer(rt);
        revil.render(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        bs.endBatch(rt);
        ps.popPose();
    }

    private static double ffx() { return fx; }
    private static double ffz() { return fz; }
}
