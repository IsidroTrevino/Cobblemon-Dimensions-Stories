import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/**
 * Compiles the pack's text shader on this machine's driver and checks where it moves glyph corners.
 * It needs a GPU, so it is not part of the Gradle build. Run it with LWJGL 3.3 (core, glfw, opengl and
 * their natives) on the classpath and a folder holding text.vsh (the pack's rendertype_text.vsh),
 * text.fsh and fog.glsl as the one argument.
 */
public final class ShaderCheck {
    static int failures;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        String fog = Files.readString(dir.resolve("fog.glsl"));
        String vsh = Files.readString(dir.resolve("text.vsh")).replace("#moj_import <fog.glsl>", fog);
        String fsh = Files.readString(dir.resolve("text.fsh")).replace("#moj_import <fog.glsl>", fog);

        if (!glfwInit()) throw new IllegalStateException("no glfw");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(64, 64, "check", 0, 0);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));

        int vs = compile(GL_VERTEX_SHADER, vsh, "vertex");
        int fs = compile(GL_FRAGMENT_SHADER, fsh, "fragment");
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glBindAttribLocation(program, 0, "Position");
        glBindAttribLocation(program, 1, "Color");
        glBindAttribLocation(program, 2, "UV0");
        glBindAttribLocation(program, 3, "UV2");
        glTransformFeedbackVaryings(program, new CharSequence[] {"gl_Position", "texCoord0", "vertexColor"}, GL_INTERLEAVED_ATTRIBS);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            System.out.println("LINK FAILED:\n" + glGetProgramInfoLog(program));
            System.exit(1);
        }
        System.out.println("compiled and linked");
        glUseProgram(program);

        // atlas: a 12x10 sprite at (20, 30) marked for the shop, an 8x24 letter cell at (100, 0)
        // marked on its left edge for the journal, and an unmarked glyph at (150, 150)
        ByteBuffer atlas = BufferUtils.createByteBuffer(256 * 256 * 4);
        mark(atlas, 20, 30, 2, 167);
        mark(atlas, 20, 39, 2, 167);
        mark(atlas, 31, 39, 2, 167);
        mark(atlas, 31, 30, 2, 167);
        mark(atlas, 100, 0, 3, 168);
        mark(atlas, 100, 23, 3, 168);
        // a backdrop plate 10x10 at (60, 60): dim fill with markers in its corners
        for (int y = 60; y < 70; y++) for (int x = 60; x < 70; x++) put(atlas, x, y, 0, 0, 0, 160);
        mark(atlas, 60, 60, 4, 167);
        mark(atlas, 60, 69, 4, 167);
        mark(atlas, 69, 69, 4, 167);
        mark(atlas, 69, 60, 4, 167);
        int texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas);
        ByteBuffer light = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 256 * 4; i++) light.put(i, (byte) 255);
        int lightmap = glGenTextures();
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, lightmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 16, 16, 0, GL_RGBA, GL_UNSIGNED_BYTE, light);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glUniform1i(glGetUniformLocation(program, "Sampler2"), 2);
        glUniform1i(glGetUniformLocation(program, "FogShape"), 0);
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, new float[] {
            1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, -11000, 1});

        // A 1919x1009 window at GUI scale 2: the ortho is 959.5 x 504.5, the game's GUI size 960 x 505.
        float gw = 959.5F;
        float gh = 504.5F;
        float[] ortho = ortho(gw, gh);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, ortho);
        float drawnX = 480 - 128;
        float drawnY = 505 - 65;

        // the dialogue on this window: k = floor(960 * 0.5 / 197) = 2, origin = (224, 505 - 40 - 290)
        mark(atlas, 20, 30, 1, 167);
        mark(atlas, 20, 39, 1, 167);
        mark(atlas, 31, 39, 1, 167);
        mark(atlas, 31, 30, 1, 167);
        mark(atlas, 100, 0, 1, 168);
        mark(atlas, 100, 23, 1, 168);
        glActiveTexture(GL_TEXTURE0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas);
        float[][] got = run(quad(drawnX + 33, drawnY + 9, 12, 10, 20, 30, 1.0F), gw, gh);
        expect(got, 0, 224 + 66, 175 + 18, "dialogue sprite top left at 2x");
        expect(got, 2, 224 + 90, 175 + 38, "dialogue sprite bottom right at 2x");
        float[][] shadow = run(quad(drawnX + 34, drawnY + 10, 12, 10, 20, 30, 63 / 255F), gw, gh);
        check(shadow[0][0] == 2.0F && shadow[3][0] == 2.0F, "dialogue shadow sent off screen");
        float[][] letter = run(quad(drawnX + 50, drawnY + 20, 8, 24, 100, 0, 1.0F), gw, gh);
        expect(letter, 0, 224 + 100, 175 + 40, "dialogue letter top left");
        expect(letter, 2, 224 + 116, 175 + 88, "dialogue letter bottom right, found through its left marker");
        // a glyph marked for an interface the shader does not know is left alone
        mark(atlas, 60, 60, 3, 167);
        mark(atlas, 60, 69, 3, 167);
        mark(atlas, 69, 69, 3, 167);
        mark(atlas, 69, 60, 3, 167);
        // the Pattern board: marked for overlay 2
        mark(atlas, 120, 120, 2, 167);
        mark(atlas, 120, 135, 2, 167);
        mark(atlas, 135, 135, 2, 167);
        mark(atlas, 135, 120, 2, 167);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlas);
        // 960x505 GUI: k = min(floor(960*0.55/176)=3, floor(505*0.8/98)=4) = 3, origin = (96, floor(505*0.45 - 168) = 59)
        float[][] tile = run(quad(drawnX + 50, drawnY + 26, 16, 16, 120, 120, 1.0F), gw, gh);
        expect(tile, 0, 96 + 150, 59 + 78, "pattern tile top left at 3x");
        expect(tile, 2, 96 + 198, 59 + 126, "pattern tile bottom right at 3x");
        float[][] patternShadow = run(quad(drawnX + 51, drawnY + 27, 16, 16, 120, 120, 63 / 255F), gw, gh);
        check(patternShadow[0][0] == 2.0F && patternShadow[3][0] == 2.0F, "pattern shadow sent off screen");
        float[][] other = run(quad(30, 40, 10, 10, 60, 60, 0.5F), gw, gh);
        expect(other, 0, 30, 40, "other markers untouched");
        float[][] plain = run(quad(10, 20, 8, 8, 150, 150, 1.0F), gw, gh);
        expect(plain, 0, 10, 20, "unmarked glyph untouched");

        // dialogue on a 427x240 GUI: k = clamp(floor(213.5/197), 1, 3) = 1, origin = (85, 240 - 40 - 145)
        float[] small = ortho(427, 240);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, small);
        float[][] bar = run(quad(213 - 128 + 28, 240 - 65 + 73, 12, 10, 20, 30, 1.0F), 427, 240);
        expect(bar, 0, 85 + 28, 55 + 73, "dialogue at 1x stays where the action bar put it");

        // pattern on 427x240: k = min(floor(427*0.55/176)=1, floor(240*0.8/98)=1) = 1, origin = (85, floor(108 - 56) = 52)
        float[][] smallTile = run(quad(213 - 128 + 50, 240 - 65 + 26, 16, 16, 120, 120, 1.0F), 427, 240);
        expect(smallTile, 0, 85 + 50, 52 + 26, "pattern tile at 1x");
        // pattern on a 1280x720 GUI (a 4K window at GUI 3): k = min(floor(1280*.55/176)=4, floor(720*.8/98)=5) = 4
        float[] big = ortho(1280, 720);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, big);
        float[][] bigTile = run(quad(640 - 128 + 50, 720 - 65 + 26, 16, 16, 120, 120, 1.0F), 1280, 720);
        expect(bigTile, 0, 128 + 200, (float) Math.floor(720 * 0.45 - 224) + 104, "pattern tile at 4x");

        System.out.println(failures == 0 ? "ALL SHADER CHECKS PASSED" : failures + " CHECKS FAILED");
        glfwDestroyWindow(window);
        glfwTerminate();
        System.exit(failures == 0 ? 0 : 1);
    }

    static float[] ortho(float w, float h) {
        // setOrtho(0, w, h, 0, 1000, 21000), column major
        return new float[] {2 / w, 0, 0, 0, 0, -2 / h, 0, 0, 0, 0, -2 / 20000F, 0, -1, 1, -22000 / 20000F, 1};
    }

    static void mark(ByteBuffer atlas, int x, int y, int overlay, int kind) {
        put(atlas, x, y, overlay, kind, 60, 7);
    }

    static void put(ByteBuffer atlas, int x, int y, int r, int g, int b, int a) {
        int i = (y * 256 + x) * 4;
        atlas.put(i, (byte) r).put(i + 1, (byte) g).put(i + 2, (byte) b).put(i + 3, (byte) a);
    }

    /** One glyph quad as the game emits it: top left, bottom left, bottom right, top right. */
    static float[] quad(float x, float y, int w, int h, int u, int v, float grey) {
        float u0 = (u + 0.01F) / 256, u1 = (u - 0.01F + w) / 256, v0 = (v + 0.01F) / 256, v1 = (v - 0.01F + h) / 256;
        return new float[] {
            x, y, u0, v0, x, y + h, u0, v1, x + w, y + h, u1, v1, x + w, y, u1, v0, grey};
    }

    /** Draws the four corners as points and reads back position, uv and colour per vertex. */
    static float[][] run(float[] q, float gw, float gh) {
        FloatBuffer data = BufferUtils.createFloatBuffer(4 * 9);
        IntBuffer light = BufferUtils.createIntBuffer(8);
        for (int i = 0; i < 4; i++) {
            data.put(q[i * 4]).put(q[i * 4 + 1]).put(0).put(q[16]).put(q[16]).put(q[16]).put(1).put(q[i * 4 + 2]).put(q[i * 4 + 3]);
            light.put(240).put(240);
        }
        data.flip();
        light.flip();
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 36, 0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 36, 12);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 36, 28);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        int lbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, lbo);
        glBufferData(GL_ARRAY_BUFFER, light, GL_STATIC_DRAW);
        glVertexAttribIPointer(3, 2, GL_INT, 8, 0);
        glEnableVertexAttribArray(3);
        int tfo = glGenBuffers();
        glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, tfo);
        glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, 4 * 10 * 4, GL_STATIC_READ);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, tfo);
        glEnable(GL_RASTERIZER_DISCARD);
        glBeginTransformFeedback(GL_POINTS);
        glDrawArrays(GL_POINTS, 0, 4);
        glEndTransformFeedback();
        glDisable(GL_RASTERIZER_DISCARD);
        FloatBuffer out = BufferUtils.createFloatBuffer(40);
        glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, out);
        float[][] result = new float[4][10];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 10; j++) result[i][j] = out.get(i * 10 + j);
            // clip space back to GUI coordinates (the shadow case stays raw)
            if (result[i][0] != 2.0F) {
                result[i][0] = (result[i][0] + 1) / 2 * gw;
                result[i][1] = (1 - result[i][1]) / 2 * gh;
            }
        }
        int error = glGetError();
        if (error != 0) {
            System.out.println("GL error " + error);
            failures++;
        }
        return result;
    }

    static void expect(float[][] got, int corner, float x, float y, String what) {
        check(Math.abs(got[corner][0] - x) < 0.01F && Math.abs(got[corner][1] - y) < 0.01F,
            what + ": expected (" + x + ", " + y + ") got (" + got[corner][0] + ", " + got[corner][1] + ")");
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static int compile(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            System.out.println(name + " FAILED:\n" + glGetShaderInfoLog(shader));
            System.exit(1);
        }
        String log = glGetShaderInfoLog(shader);
        if (!log.isBlank()) System.out.println(name + " log: " + log);
        return shader;
    }
}
