/*
 * Lucide Icons geometry from com.composables:icons-lucide-android:1.1.0.
 * https://github.com/lucide-icons/lucide/blob/0.468.0/LICENSE
 * https://github.com/composablehorizons/composeicons/blob/82298687229158b74d0e03c5c953a1cf445e6813/LICENSE
 *
 * ISC License
 *
 * Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT). All other copyright (c) for Lucide are held by Lucide Contributors 2022.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * MIT License
 *
 * Copyright (c) 2024 Composable Horizons
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object HomeIcons {
    val ListTree: ImageVector by lazy {
        lucideVector("ListTree") {
            lucidePath {
                moveTo(21f, 12f)
                horizontalLineToRelative(-8f)
            }
            lucidePath {
                moveTo(21f, 6f)
                horizontalLineTo(8f)
            }
            lucidePath {
                moveTo(21f, 18f)
                horizontalLineToRelative(-8f)
            }
            lucidePath {
                moveTo(3f, 6f)
                verticalLineToRelative(4f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(3f)
            }
            lucidePath {
                moveTo(3f, 10f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(3f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        lucideVector("Copy") {
            lucidePath {
                moveTo(10f, 8f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 10f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 22f)
                horizontalLineTo(10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 20f)
                verticalLineTo(10f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 8f)
                close()
            }
            lucidePath {
                moveTo(4f, 16f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                horizontalLineToRelative(10f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            }
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucideVector("RefreshCw") {
            lucidePath {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, -9f)
                arcToRelative(9.75f, 9.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.74f, 2.74f)
                lineTo(21f, 8f)
            }
            lucidePath {
                moveTo(21f, 3f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(-5f)
            }
            lucidePath {
                moveTo(21f, 12f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -9f, 9f)
                arcToRelative(9.75f, 9.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -6.74f, -2.74f)
                lineTo(3f, 16f)
            }
            lucidePath {
                moveTo(8f, 16f)
                horizontalLineTo(3f)
                verticalLineToRelative(5f)
            }
        }
    }

    val Volume2: ImageVector by lazy {
        lucideVector("Volume2") {
            lucidePath {
                moveTo(11f, 5f)
                lineTo(6f, 9f)
                lineTo(2f, 9f)
                lineTo(2f, 15f)
                lineTo(6f, 15f)
                lineTo(11f, 19f)
                lineTo(11f, 5f)
                close()
            }
            lucidePath {
                moveTo(15.54f, 8.46f)
                arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 7.07f)
            }
            lucidePath {
                moveTo(19.07f, 4.93f)
                arcToRelative(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 14.14f)
            }
        }
    }

    val CircleStop: ImageVector by lazy {
        lucideVector("CircleStop") {
            lucidePath {
                moveTo(22f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 22f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 12f)
                close()
            }
            lucidePath {
                moveTo(9f, 9f)
                horizontalLineTo(15f)
                verticalLineTo(15f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    val Languages: ImageVector by lazy {
        lucideVector("Languages") {
            lucidePath {
                moveTo(5f, 8f)
                lineToRelative(6f, 6f)
            }
            lucidePath {
                moveTo(4f, 14f)
                lineToRelative(6f, -6f)
                lineToRelative(2f, -3f)
            }
            lucidePath {
                moveTo(2f, 5f)
                horizontalLineToRelative(12f)
            }
            lucidePath {
                moveTo(7f, 2f)
                horizontalLineToRelative(1f)
            }
            lucidePath {
                moveTo(22f, 22f)
                lineToRelative(-5f, -10f)
                lineToRelative(-5f, 10f)
            }
            lucidePath {
                moveTo(14f, 18f)
                horizontalLineToRelative(6f)
            }
        }
    }

    val Ellipsis: ImageVector by lazy {
        lucideVector("Ellipsis") {
            lucidePath {
                moveTo(13f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 13f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 11f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 13f, 12f)
                close()
            }
            lucidePath {
                moveTo(20f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 13f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 12f)
                close()
            }
            lucidePath {
                moveTo(6f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5f, 13f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 12f)
                arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6f, 12f)
                close()
            }
        }
    }
}

private fun lucideVector(
    name: String,
    paths: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(paths).build()

private fun ImageVector.Builder.lucidePath(nodes: PathBuilder.() -> Unit) {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineMiter = 1f,
        pathBuilder = nodes,
    )
}
