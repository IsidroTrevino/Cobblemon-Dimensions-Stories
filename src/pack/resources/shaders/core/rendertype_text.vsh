#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// Cobblemon Dimensions Stories sends its walking dialogue box and its Pattern game
// as action bar text. Every glyph they use has a marker in its corner pixels. This
// shader finds those glyphs, drops the shadow the action bar gives them, and scales
// the picture with the window the way the old client drew it. Every other piece of text is drawn
// exactly as vanilla draws it.

// The marker pixel: too faint to draw, green says sprite or letter, red says which interface.
const int MARKER_ALPHA = 7;
const int MARKER_SPRITE = 167;
const int MARKER_TEXT = 168;
const int MARKER_BLUE = 60;

const int DIALOGUE = 1;
const int PATTERN = 2;

// Row of the Pattern sheet halfway down the board and its hints.
const float PATTERN_MIDDLE = 56.0;

// Colours arrive lifted into this range. The action bar's shadow copy is a quarter
// as bright, so it always lands under the floor, which is how it is told apart.
const float COLOUR_FLOOR = 72.0 / 255.0;
const float COLOUR_RANGE = 183.0 / 255.0;
const float SHADOW_LINE = 68.0 / 255.0;

// Letters sit in cells this wide and are only marked on the left edge, so the
// right-hand corners of a letter look this far back for the marker.
const int TEXT_CELL = 8;

// The corner pixel under each vertex of a glyph, in the order the game emits them:
// top left, bottom left, bottom right, top right.
const ivec2 CORNER[4] = ivec2[4](ivec2(0, 0), ivec2(0, -1), ivec2(-1, -1), ivec2(-1, 0));

int marker(ivec2 texel, int kind) {
    ivec4 pixel = ivec4(round(texelFetch(Sampler0, texel, 0) * 255.0));
    return pixel.a == MARKER_ALPHA && pixel.g == kind && pixel.b == MARKER_BLUE ? pixel.r : 0;
}

void main() {
    vec3 position = Position;
    vec4 colour = Color;
    texCoord0 = UV0;

    // The box is only ever drawn flat on the screen, never in the world.
    if (ProjMat[2][3] == 0.0) {
        int corner = gl_VertexID % 4;
        ivec2 size = textureSize(Sampler0, 0);
        ivec2 texel = ivec2(floor(UV0 * vec2(size) + 0.5)) + CORNER[corner];
        int overlay = marker(texel, MARKER_SPRITE);
        if (overlay == 0) {
            overlay = marker(texel, MARKER_TEXT);
        }
        if (overlay == 0 && corner >= 2) {
            overlay = marker(texel - ivec2(TEXT_CELL - 1, 0), MARKER_TEXT);
        }

        if (overlay == DIALOGUE || overlay == PATTERN) {
            if (max(Color.r, max(Color.g, Color.b)) < SHADOW_LINE) {
                // The action bar's drop shadow: send it outside the screen.
                gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                vertexDistance = 0.0;
                vertexColor = vec4(0.0);
                return;
            }
            colour.rgb = clamp((Color.rgb - COLOUR_FLOOR) / COLOUR_RANGE, 0.0, 1.0);

            // The GUI size, rounded up the way the game rounds its scaled window.
            vec2 screen = ceil(vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]) - 0.001);
            // Where the action bar put the top left corner of the 256 pixel sheet.
            vec2 drawn = vec2(floor(screen.x / 2.0) - 128.0, screen.y - 65.0);
            float scale;
            vec2 origin;
            if (overlay == DIALOGUE) {
                // About half the screen wide, at most three times, resting 40 above the bottom.
                scale = clamp(floor(screen.x * 0.5 / 197.0 + 0.001), 1.0, 3.0);
                origin = vec2(floor((screen.x - 256.0 * scale) / 2.0), screen.y - 40.0 - 145.0 * scale);
            } else {
                // The board is 176 wide and its hints end 98 rows down. Over half the
                // width (11/20), within four fifths of the height, centred a little above
                // middle (9/20 down). A GPU's division can come out a hair under an exact
                // whole number, so the floors are nudged up a little.
                scale = clamp(min(floor(screen.x * 11.0 / 3520.0 + 0.001), floor(screen.y * 4.0 / 490.0 + 0.001)), 1.0, 6.0);
                origin = vec2(floor((screen.x - 256.0 * scale) / 2.0),
                    floor((screen.y * 9.0 - 20.0 * PATTERN_MIDDLE * scale) / 20.0 + 0.001));
            }
            position.xy = origin + (position.xy - drawn) * scale;
        }
    }

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(position, FogShape);
    vertexColor = colour * texelFetch(Sampler2, UV2 / 16, 0);
}
