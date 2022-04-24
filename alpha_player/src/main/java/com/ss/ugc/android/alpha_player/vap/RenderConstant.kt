/*
 * Tencent is pleased to support the open source community by making vap available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ss.ugc.android.alpha_player.vap

object RenderConstant {
    const val VERTEXT_SHADER_TEST = ""

    const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "\n" +
            "attribute vec4 vPosition;\n" +
            "attribute vec4 vTexCoordinateAlpha;\n" +
            "attribute vec4 vTexCoordinateRgb;\n" +
            "varying vec2 v_TexCoordinateAlpha;\n" +
            "varying vec2 v_TexCoordinateRgb;\n" +
            "\n" +
            "void main() {\n" +
            "    v_TexCoordinateAlpha = vec2(vTexCoordinateAlpha.x, vTexCoordinateAlpha.y);\n" +
            "    v_TexCoordinateRgb = vec2(vTexCoordinateRgb.x, vTexCoordinateRgb.y);\n" +
            //"    float midX = (uSTMatrix * vec4(0.5, 0.0, 0.0, 1.0)).x;\n" +
            //"    v_TexCoordinateAlpha = (uSTMatrix * vTexCoordinateAlpha).xy;\n" +
            //"    v_TexCoordinateRgb = (uSTMatrix * vTexCoordinateRgb).xy;\n" +
            "    gl_Position = uMVPMatrix * vPosition;\n" +
            "}"


    const val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES texture;\n" +
            "varying vec2 v_TexCoordinateAlpha;\n" +
            "varying vec2 v_TexCoordinateRgb;\n" +
            "\n" +
            "void main () {\n" +
            "    vec4 alphaColor = texture2D(texture, v_TexCoordinateAlpha);\n" +
            "    vec4 rgbColor = texture2D(texture, v_TexCoordinateRgb);\n" +
            "    gl_FragColor = vec4(rgbColor.r, rgbColor.g, rgbColor.b, alphaColor.r);\n" +
            "}"


    private val VERTEX_SHADER1 = """uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

attribute vec4 aPosition;
attribute vec4 aTextureCoord;

varying vec2 vTextureCoord;
varying vec2 r_TexCoordinate;
void main() {
    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
         float midX = (uSTMatrix * vec4(0.5, 0.0, 0.0, 1.0)).x;
          r_TexCoordinate = vec2(vTextureCoord.x + midX, vTextureCoord.y);
          gl_Position = uMVPMatrix * aPosition;
}
"""

    // Simple fragment shader for use with "normal" 2D textures.
    private val FRAGMENT_SHADER_2D = """#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
varying vec2 r_TexCoordinate;
uniform samplerExternalOES sTexture;

void main() {
    vec4 color = texture2D(sTexture, vTextureCoord);
     vec4 alpha = texture2D(sTexture, vec2(r_TexCoordinate.x, r_TexCoordinate.y));
      gl_FragColor = vec4(color.r, color.g, color.b, alpha.r);
}
"""
}