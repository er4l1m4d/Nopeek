package com.nopeek.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
MIT License

Copyright (c) 2020 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

// Hand-vendored Phosphor icon set (Task 7). The icons-phosphor library was
// rejected by the Task 0 spike, so the T7 set is vendored here instead.
// Source — first priority per the plan's icon-vendoring rule: individual
// composables.com Phosphor icon .kt files
// (https://composables.com/download/icons/phosphor/<slug>.kt with
// ?v=fill / ?v=bold for the Fill/Bold weights). Path data is transcribed
// verbatim from those files: no hand-traced glyphs, no Material substitutes.
// Set: nav icons in Regular + Fill (House, Flag, ClockCounterClockwise, User,
// Plus) + Plus Bold (CreateAction), plus Regular ArrowRight, CheckCircle,
// WarningCircle, XCircle (status-chip leading icons), ArrowLeft (back
// actions), Copy (copyable addresses), LockKey (Recovery Lock), Wallet
// (Profile wallet card), Trophy (results/empty states).

object Phosphor {

    val House: ImageVector
        get() {
            if (_House != null) return _House!!
            _House = ImageVector.Builder(
                name = "house",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(219.31f, 108.68f)
                    lineToRelative(-80f, -80f)
                    arcToRelative(16f, 16f, 0f, false, false, -22.62f, 0f)
                    lineToRelative(-80f, 80f)
                    arcTo(15.87f, 15.87f, 0f, false, false, 32f, 120f)
                    verticalLineToRelative(96f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, 8f)
                    horizontalLineToRelative(64f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, -8f)
                    verticalLineTo(160f)
                    horizontalLineToRelative(32f)
                    verticalLineToRelative(56f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, 8f)
                    horizontalLineToRelative(64f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, -8f)
                    verticalLineTo(120f)
                    arcTo(15.87f, 15.87f, 0f, false, false, 219.31f, 108.68f)
                    close()
                    moveTo(208f, 208f)
                    horizontalLineTo(160f)
                    verticalLineTo(152f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, -8f)
                    horizontalLineTo(104f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                    verticalLineToRelative(56f)
                    horizontalLineTo(48f)
                    verticalLineTo(120f)
                    lineToRelative(80f, -80f)
                    lineToRelative(80f, 80f)
                    close()
                }
            }.build()
            return _House!!
        }

    val HouseFill: ImageVector
        get() {
            if (_HouseFill != null) return _HouseFill!!
            _HouseFill = ImageVector.Builder(
                name = "house-fill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(224f, 120f)
                    verticalLineToRelative(96f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                    horizontalLineTo(160f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                    verticalLineTo(164f)
                    arcToRelative(4f, 4f, 0f, false, false, -4f, -4f)
                    horizontalLineTo(108f)
                    arcToRelative(4f, 4f, 0f, false, false, -4f, 4f)
                    verticalLineToRelative(52f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                    horizontalLineTo(40f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                    verticalLineTo(120f)
                    arcToRelative(16f, 16f, 0f, false, true, 4.69f, -11.31f)
                    lineToRelative(80f, -80f)
                    arcToRelative(16f, 16f, 0f, false, true, 22.62f, 0f)
                    lineToRelative(80f, 80f)
                    arcTo(16f, 16f, 0f, false, true, 224f, 120f)
                    close()
                }
            }.build()
            return _HouseFill!!
        }

    val Flag: ImageVector
        get() {
            if (_Flag != null) return _Flag!!
            _Flag = ImageVector.Builder(
                name = "flag",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(42.76f, 50f)
                    arcTo(8f, 8f, 0f, false, false, 40f, 56f)
                    verticalLineTo(224f)
                    arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                    verticalLineTo(179.77f)
                    curveToRelative(26.79f, -21.16f, 49.87f, -9.75f, 76.45f, 3.41f)
                    curveToRelative(16.4f, 8.11f, 34.06f, 16.85f, 53f, 16.85f)
                    curveToRelative(13.93f, 0f, 28.54f, -4.75f, 43.82f, -18f)
                    arcToRelative(8f, 8f, 0f, false, false, 2.76f, -6f)
                    verticalLineTo(56f)
                    arcTo(8f, 8f, 0f, false, false, 218.76f, 50f)
                    curveToRelative(-28f, 24.23f, -51.72f, 12.49f, -79.21f, -1.12f)
                    curveTo(111.07f, 34.76f, 78.78f, 18.79f, 42.76f, 50f)
                    close()
                    moveTo(216f, 172.25f)
                    curveToRelative(-26.79f, 21.16f, -49.87f, 9.74f, -76.45f, -3.41f)
                    curveToRelative(-25f, -12.35f, -52.81f, -26.13f, -83.55f, -8.4f)
                    verticalLineTo(59.79f)
                    curveToRelative(26.79f, -21.16f, 49.87f, -9.75f, 76.45f, 3.4f)
                    curveToRelative(25f, 12.35f, 52.82f, 26.13f, 83.55f, 8.4f)
                    close()
                }
            }.build()
            return _Flag!!
        }

    val FlagFill: ImageVector
        get() {
            if (_FlagFill != null) return _FlagFill!!
            _FlagFill = ImageVector.Builder(
                name = "flag-fill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(232f, 56f)
                    verticalLineTo(176f)
                    arcToRelative(8f, 8f, 0f, false, true, -2.76f, 6f)
                    curveToRelative(-15.28f, 13.23f, -29.89f, 18f, -43.82f, 18f)
                    curveToRelative(-18.91f, 0f, -36.57f, -8.74f, -53f, -16.85f)
                    curveTo(105.87f, 170f, 82.79f, 158.61f, 56f, 179.77f)
                    verticalLineTo(224f)
                    arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                    verticalLineTo(56f)
                    arcToRelative(8f, 8f, 0f, false, true, 2.77f, -6f)
                    horizontalLineToRelative(0f)
                    curveToRelative(36f, -31.18f, 68.31f, -15.21f, 96.79f, -1.12f)
                    curveTo(167f, 62.46f, 190.79f, 74.2f, 218.76f, 50f)
                    arcTo(8f, 8f, 0f, false, true, 232f, 56f)
                    close()
                }
            }.build()
            return _FlagFill!!
        }

    val ClockCounterClockwise: ImageVector
        get() {
            if (_ClockCounterClockwise != null) return _ClockCounterClockwise!!
            _ClockCounterClockwise = ImageVector.Builder(
                name = "clock-counter-clockwise",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(136f, 80f)
                    verticalLineToRelative(43.47f)
                    lineToRelative(36.12f, 21.67f)
                    arcToRelative(8f, 8f, 0f, false, true, -8.24f, 13.72f)
                    lineToRelative(-40f, -24f)
                    arcTo(8f, 8f, 0f, false, true, 120f, 128f)
                    verticalLineTo(80f)
                    arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                    close()
                    moveToRelative(-8f, -48f)
                    arcTo(95.44f, 95.44f, 0f, false, false, 60.08f, 60.15f)
                    curveTo(52.81f, 67.51f, 46.35f, 74.59f, 40f, 82f)
                    verticalLineTo(64f)
                    arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                    verticalLineToRelative(40f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, 8f)
                    horizontalLineTo(72f)
                    arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                    horizontalLineTo(49f)
                    curveToRelative(7.15f, -8.42f, 14.27f, -16.35f, 22.39f, -24.57f)
                    arcToRelative(80f, 80f, 0f, true, true, 1.66f, 114.75f)
                    arcToRelative(8f, 8f, 0f, true, false, -11f, 11.64f)
                    arcTo(96f, 96f, 0f, true, false, 128f, 32f)
                    close()
                }
            }.build()
            return _ClockCounterClockwise!!
        }

    val ClockCounterClockwiseFill: ImageVector
        get() {
            if (_ClockCounterClockwiseFill != null) return _ClockCounterClockwiseFill!!
            _ClockCounterClockwiseFill = ImageVector.Builder(
                name = "clock-counter-clockwise-fill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(224f, 128f)
                    arcTo(96f, 96f, 0f, false, true, 62.11f, 197.82f)
                    arcToRelative(8f, 8f, 0f, true, true, 11f, -11.64f)
                    arcTo(80f, 80f, 0f, true, false, 71.43f, 71.43f)
                    curveTo(67.9f, 75f, 64.58f, 78.51f, 61.35f, 82f)
                    lineTo(77.66f, 98.34f)
                    arcTo(8f, 8f, 0f, false, true, 72f, 112f)
                    horizontalLineTo(32f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                    verticalLineTo(64f)
                    arcToRelative(8f, 8f, 0f, false, true, 13.66f, -5.66f)
                    lineTo(50f, 70.7f)
                    curveToRelative(3.22f, -3.49f, 6.54f, -7f, 10.06f, -10.55f)
                    arcTo(96f, 96f, 0f, false, true, 224f, 128f)
                    close()
                    moveTo(128f, 72f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                    verticalLineToRelative(48f)
                    arcToRelative(8f, 8f, 0f, false, false, 3.88f, 6.86f)
                    lineToRelative(40f, 24f)
                    arcToRelative(8f, 8f, 0f, true, false, 8.24f, -13.72f)
                    lineTo(136f, 123.47f)
                    verticalLineTo(80f)
                    arcTo(8f, 8f, 0f, false, false, 128f, 72f)
                    close()
                }
            }.build()
            return _ClockCounterClockwiseFill!!
        }

    val User: ImageVector
        get() {
            if (_User != null) return _User!!
            _User = ImageVector.Builder(
                name = "user",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(230.92f, 212f)
                    curveToRelative(-15.23f, -26.33f, -38.7f, -45.21f, -66.09f, -54.16f)
                    arcToRelative(72f, 72f, 0f, true, false, -73.66f, 0f)
                    curveTo(63.78f, 166.78f, 40.31f, 185.66f, 25.08f, 212f)
                    arcToRelative(8f, 8f, 0f, true, false, 13.85f, 8f)
                    curveToRelative(18.84f, -32.56f, 52.14f, -52f, 89.07f, -52f)
                    reflectiveCurveToRelative(70.23f, 19.44f, 89.07f, 52f)
                    arcToRelative(8f, 8f, 0f, true, false, 13.85f, -8f)
                    close()
                    moveTo(72f, 96f)
                    arcToRelative(56f, 56f, 0f, true, true, 56f, 56f)
                    arcTo(56.06f, 56.06f, 0f, false, true, 72f, 96f)
                    close()
                }
            }.build()
            return _User!!
        }

    val UserFill: ImageVector
        get() {
            if (_UserFill != null) return _UserFill!!
            _UserFill = ImageVector.Builder(
                name = "user-fill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(230.93f, 220f)
                    arcToRelative(8f, 8f, 0f, false, true, -6.93f, 4f)
                    horizontalLineTo(32f)
                    arcToRelative(8f, 8f, 0f, false, true, -6.92f, -12f)
                    curveToRelative(15.23f, -26.33f, 38.7f, -45.21f, 66.09f, -54.16f)
                    arcToRelative(72f, 72f, 0f, true, true, 73.66f, 0f)
                    curveToRelative(27.39f, 8.95f, 50.86f, 27.83f, 66.09f, 54.16f)
                    arcTo(8f, 8f, 0f, false, true, 230.93f, 220f)
                    close()
                }
            }.build()
            return _UserFill!!
        }

    val Plus: ImageVector
        get() {
            if (_Plus != null) return _Plus!!
            _Plus = ImageVector.Builder(
                name = "plus",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(224f, 128f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                    horizontalLineTo(136f)
                    verticalLineToRelative(80f)
                    arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                    verticalLineTo(136f)
                    horizontalLineTo(40f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                    horizontalLineToRelative(80f)
                    verticalLineTo(40f)
                    arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                    verticalLineToRelative(80f)
                    horizontalLineToRelative(80f)
                    arcTo(8f, 8f, 0f, false, true, 224f, 128f)
                    close()
                }
            }.build()
            return _Plus!!
        }

    val PlusFill: ImageVector
        get() {
            if (_PlusFill != null) return _PlusFill!!
            _PlusFill = ImageVector.Builder(
                name = "plus-fill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(208f, 32f)
                    horizontalLineTo(48f)
                    arcTo(16f, 16f, 0f, false, false, 32f, 48f)
                    verticalLineTo(208f)
                    arcToRelative(16f, 16f, 0f, false, false, 16f, 16f)
                    horizontalLineTo(208f)
                    arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                    verticalLineTo(48f)
                    arcTo(16f, 16f, 0f, false, false, 208f, 32f)
                    close()
                    moveTo(184f, 136f)
                    horizontalLineTo(136f)
                    verticalLineToRelative(48f)
                    arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                    verticalLineTo(136f)
                    horizontalLineTo(72f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                    horizontalLineToRelative(48f)
                    verticalLineTo(72f)
                    arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                    verticalLineToRelative(48f)
                    horizontalLineToRelative(48f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                    close()
                }
            }.build()
            return _PlusFill!!
        }

    val PlusBold: ImageVector
        get() {
            if (_PlusBold != null) return _PlusBold!!
            _PlusBold = ImageVector.Builder(
                name = "plus-bold",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(228f, 128f)
                    arcToRelative(12f, 12f, 0f, false, true, -12f, 12f)
                    horizontalLineTo(140f)
                    verticalLineToRelative(76f)
                    arcToRelative(12f, 12f, 0f, false, true, -24f, 0f)
                    verticalLineTo(140f)
                    horizontalLineTo(40f)
                    arcToRelative(12f, 12f, 0f, false, true, 0f, -24f)
                    horizontalLineToRelative(76f)
                    verticalLineTo(40f)
                    arcToRelative(12f, 12f, 0f, false, true, 24f, 0f)
                    verticalLineToRelative(76f)
                    horizontalLineToRelative(76f)
                    arcTo(12f, 12f, 0f, false, true, 228f, 128f)
                    close()
                }
            }.build()
            return _PlusBold!!
        }

    val ArrowRight: ImageVector
        get() {
            if (_ArrowRight != null) return _ArrowRight!!
            _ArrowRight = ImageVector.Builder(
                name = "arrow-right",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(221.66f, 133.66f)
                    lineToRelative(-72f, 72f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                    lineTo(196.69f, 136f)
                    horizontalLineTo(40f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                    horizontalLineTo(196.69f)
                    lineTo(138.34f, 61.66f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, -11.32f)
                    lineToRelative(72f, 72f)
                    arcTo(8f, 8f, 0f, false, true, 221.66f, 133.66f)
                    close()
                }
            }.build()
            return _ArrowRight!!
        }

    val CheckCircle: ImageVector
        get() {
            if (_CheckCircle != null) return _CheckCircle!!
            _CheckCircle = ImageVector.Builder(
                name = "check-circle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(173.66f, 98.34f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, 11.32f)
                    lineToRelative(-56f, 56f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, 0f)
                    lineToRelative(-24f, -24f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, -11.32f)
                    lineTo(112f, 148.69f)
                    lineToRelative(50.34f, -50.35f)
                    arcTo(8f, 8f, 0f, false, true, 173.66f, 98.34f)
                    close()
                    moveTo(232f, 128f)
                    arcTo(104f, 104f, 0f, true, true, 128f, 24f)
                    arcTo(104.11f, 104.11f, 0f, false, true, 232f, 128f)
                    close()
                    moveToRelative(-16f, 0f)
                    arcToRelative(88f, 88f, 0f, true, false, -88f, 88f)
                    arcTo(88.1f, 88.1f, 0f, false, false, 216f, 128f)
                    close()
                }
            }.build()
            return _CheckCircle!!
        }

    val WarningCircle: ImageVector
        get() {
            if (_WarningCircle != null) return _WarningCircle!!
            _WarningCircle = ImageVector.Builder(
                name = "warning-circle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(128f, 24f)
                    arcTo(104f, 104f, 0f, true, false, 232f, 128f)
                    arcTo(104.11f, 104.11f, 0f, false, false, 128f, 24f)
                    close()
                    moveToRelative(0f, 192f)
                    arcToRelative(88f, 88f, 0f, true, true, 88f, -88f)
                    arcTo(88.1f, 88.1f, 0f, false, true, 128f, 216f)
                    close()
                    moveToRelative(-8f, -80f)
                    verticalLineTo(80f)
                    arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                    verticalLineToRelative(56f)
                    arcToRelative(8f, 8f, 0f, false, true, -16f, 0f)
                    close()
                    moveToRelative(20f, 36f)
                    arcToRelative(12f, 12f, 0f, true, true, -12f, -12f)
                    arcTo(12f, 12f, 0f, false, true, 140f, 172f)
                    close()
                }
            }.build()
            return _WarningCircle!!
        }

    val XCircle: ImageVector
        get() {
            if (_XCircle != null) return _XCircle!!
            _XCircle = ImageVector.Builder(
                name = "x-circle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(165.66f, 101.66f)
                    lineTo(139.31f, 128f)
                    lineToRelative(26.35f, 26.34f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, 11.32f)
                    lineTo(128f, 139.31f)
                    lineToRelative(-26.34f, 26.35f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                    lineTo(116.69f, 128f)
                    lineTo(90.34f, 101.66f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, -11.32f)
                    lineTo(128f, 116.69f)
                    lineToRelative(26.34f, -26.35f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, 11.32f)
                    close()
                    moveTo(232f, 128f)
                    arcTo(104f, 104f, 0f, true, true, 128f, 24f)
                    arcTo(104.11f, 104.11f, 0f, false, true, 232f, 128f)
                    close()
                    moveToRelative(-16f, 0f)
                    arcToRelative(88f, 88f, 0f, true, false, -88f, 88f)
                    arcTo(88.1f, 88.1f, 0f, false, false, 216f, 128f)
                    close()
                }
            }.build()
            return _XCircle!!
        }

    val ArrowLeft: ImageVector
        get() {
            if (_ArrowLeft != null) return _ArrowLeft!!
            _ArrowLeft = ImageVector.Builder(
                name = "arrow-left",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(224f, 128f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                    horizontalLineTo(59.31f)
                    lineToRelative(58.35f, 58.34f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, 11.32f)
                    lineToRelative(-72f, -72f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, -11.32f)
                    lineToRelative(72f, -72f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, 11.32f)
                    lineTo(59.31f, 120f)
                    horizontalLineTo(216f)
                    arcTo(8f, 8f, 0f, false, true, 224f, 128f)
                    close()
                }
            }.build()
            return _ArrowLeft!!
        }

    val Copy: ImageVector
        get() {
            if (_Copy != null) return _Copy!!
            _Copy = ImageVector.Builder(
                name = "copy",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(216f, 32f)
                    horizontalLineTo(88f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                    verticalLineTo(80f)
                    horizontalLineTo(40f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                    verticalLineTo(216f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, 8f)
                    horizontalLineTo(168f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, -8f)
                    verticalLineTo(176f)
                    horizontalLineToRelative(40f)
                    arcToRelative(8f, 8f, 0f, false, false, 8f, -8f)
                    verticalLineTo(40f)
                    arcTo(8f, 8f, 0f, false, false, 216f, 32f)
                    close()
                    moveTo(160f, 208f)
                    horizontalLineTo(48f)
                    verticalLineTo(96f)
                    horizontalLineTo(160f)
                    close()
                    moveToRelative(48f, -48f)
                    horizontalLineTo(176f)
                    verticalLineTo(88f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, -8f)
                    horizontalLineTo(96f)
                    verticalLineTo(48f)
                    horizontalLineTo(208f)
                    close()
                }
            }.build()
            return _Copy!!
        }

    val LockKey: ImageVector
        get() {
            if (_LockKey != null) return _LockKey!!
            _LockKey = ImageVector.Builder(
                name = "lock-key",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(128f, 112f)
                    arcToRelative(28f, 28f, 0f, false, false, -8f, 54.83f)
                    verticalLineTo(184f)
                    arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                    verticalLineTo(166.83f)
                    arcTo(28f, 28f, 0f, false, false, 128f, 112f)
                    close()
                    moveToRelative(0f, 40f)
                    arcToRelative(12f, 12f, 0f, true, true, 12f, -12f)
                    arcTo(12f, 12f, 0f, false, true, 128f, 152f)
                    close()
                    moveToRelative(80f, -72f)
                    horizontalLineTo(176f)
                    verticalLineTo(56f)
                    arcToRelative(48f, 48f, 0f, false, false, -96f, 0f)
                    verticalLineTo(80f)
                    horizontalLineTo(48f)
                    arcTo(16f, 16f, 0f, false, false, 32f, 96f)
                    verticalLineTo(208f)
                    arcToRelative(16f, 16f, 0f, false, false, 16f, 16f)
                    horizontalLineTo(208f)
                    arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                    verticalLineTo(96f)
                    arcTo(16f, 16f, 0f, false, false, 208f, 80f)
                    close()
                    moveTo(96f, 56f)
                    arcToRelative(32f, 32f, 0f, false, true, 64f, 0f)
                    verticalLineTo(80f)
                    horizontalLineTo(96f)
                    close()
                    moveTo(208f, 208f)
                    horizontalLineTo(48f)
                    verticalLineTo(96f)
                    horizontalLineTo(208f)
                    verticalLineTo(208f)
                    close()
                }
            }.build()
            return _LockKey!!
        }

    val Wallet: ImageVector
        get() {
            if (_Wallet != null) return _Wallet!!
            _Wallet = ImageVector.Builder(
                name = "wallet",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(216f, 64f)
                    horizontalLineTo(56f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                    horizontalLineTo(192f)
                    arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                    horizontalLineTo(56f)
                    arcTo(24f, 24f, 0f, false, false, 32f, 56f)
                    verticalLineTo(184f)
                    arcToRelative(24f, 24f, 0f, false, false, 24f, 24f)
                    horizontalLineTo(216f)
                    arcToRelative(16f, 16f, 0f, false, false, 16f, -16f)
                    verticalLineTo(80f)
                    arcTo(16f, 16f, 0f, false, false, 216f, 64f)
                    close()
                    moveToRelative(0f, 128f)
                    horizontalLineTo(56f)
                    arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                    verticalLineTo(78.63f)
                    arcTo(23.84f, 23.84f, 0f, false, false, 56f, 80f)
                    horizontalLineTo(216f)
                    close()
                    moveToRelative(-48f, -60f)
                    arcToRelative(12f, 12f, 0f, true, true, 12f, 12f)
                    arcTo(12f, 12f, 0f, false, true, 168f, 132f)
                    close()
                }
            }.build()
            return _Wallet!!
        }

    val Trophy: ImageVector
        get() {
            if (_Trophy != null) return _Trophy!!
            _Trophy = ImageVector.Builder(
                name = "trophy",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(232f, 64f)
                    horizontalLineTo(208f)
                    verticalLineTo(48f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, -8f)
                    horizontalLineTo(56f)
                    arcToRelative(8f, 8f, 0f, false, false, -8f, 8f)
                    verticalLineTo(64f)
                    horizontalLineTo(24f)
                    arcTo(16f, 16f, 0f, false, false, 8f, 80f)
                    verticalLineTo(96f)
                    arcToRelative(40f, 40f, 0f, false, false, 40f, 40f)
                    horizontalLineToRelative(3.65f)
                    arcTo(80.13f, 80.13f, 0f, false, false, 120f, 191.61f)
                    verticalLineTo(216f)
                    horizontalLineTo(96f)
                    arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                    horizontalLineToRelative(64f)
                    arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                    horizontalLineTo(136f)
                    verticalLineTo(191.58f)
                    curveToRelative(31.94f, -3.23f, 58.44f, -25.64f, 68.08f, -55.58f)
                    horizontalLineTo(208f)
                    arcToRelative(40f, 40f, 0f, false, false, 40f, -40f)
                    verticalLineTo(80f)
                    arcTo(16f, 16f, 0f, false, false, 232f, 64f)
                    close()
                    moveTo(48f, 120f)
                    arcTo(24f, 24f, 0f, false, true, 24f, 96f)
                    verticalLineTo(80f)
                    horizontalLineTo(48f)
                    verticalLineToRelative(32f)
                    quadToRelative(0f, 4f, 0.39f, 8f)
                    close()
                    moveToRelative(144f, -8.9f)
                    curveToRelative(0f, 35.52f, -29f, 64.64f, -64f, 64.9f)
                    arcToRelative(64f, 64f, 0f, false, true, -64f, -64f)
                    verticalLineTo(56f)
                    horizontalLineTo(192f)
                    close()
                    moveTo(232f, 96f)
                    arcToRelative(24f, 24f, 0f, false, true, -24f, 24f)
                    horizontalLineToRelative(-0.5f)
                    arcToRelative(81.81f, 81.81f, 0f, false, false, 0.5f, -8.9f)
                    verticalLineTo(80f)
                    horizontalLineToRelative(24f)
                    close()
                }
            }.build()
            return _Trophy!!
        }

    val Check: ImageVector
        get() {
            if (_Check != null) return _Check!!
            _Check = ImageVector.Builder(
                name = "check",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(173.66f, 98.34f)
                    arcToRelative(8f, 8f, 0f, false, true, 0f, 11.32f)
                    lineToRelative(-56f, 56f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, 0f)
                    lineToRelative(-24f, -24f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, -11.32f)
                    lineTo(112f, 148.69f)
                    lineToRelative(50.34f, -50.35f)
                    arcTo(8f, 8f, 0f, false, true, 173.66f, 98.34f)
                    close()
                }
            }.build()
            return _Check!!
        }

    val CaretRight: ImageVector
        get() {
            if (_CaretRight != null) return _CaretRight!!
            _CaretRight = ImageVector.Builder(
                name = "caret-right",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(181.66f, 133.66f)
                    lineToRelative(-80f, 80f)
                    arcToRelative(8f, 8f, 0f, false, true, -11.32f, -11.32f)
                    lineTo(164.69f, 128f)
                    lineTo(90.34f, 53.66f)
                    arcToRelative(8f, 8f, 0f, false, true, 11.32f, -11.32f)
                    lineToRelative(80f, 80f)
                    arcTo(8f, 8f, 0f, false, true, 181.66f, 133.66f)
                    close()
                }
            }.build()
            return _CaretRight!!
        }

    val DotsThree: ImageVector
        get() {
            if (_DotsThree != null) return _DotsThree!!
            _DotsThree = ImageVector.Builder(
                name = "dots-three",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 256f,
                viewportHeight = 256f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    for (cx in floatArrayOf(60f, 128f, 196f)) {
                        moveTo(cx + 32f, 128f)
                        arcTo(32f, 32f, 0f, false, true, cx, 160f)
                        arcTo(32f, 32f, 0f, false, true, cx - 32f, 128f)
                        arcTo(32f, 32f, 0f, false, true, cx, 96f)
                        arcTo(32f, 32f, 0f, false, true, cx + 32f, 128f)
                        close()
                    }
                }
            }.build()
            return _DotsThree!!
        }

    private var _House: ImageVector? = null
    private var _HouseFill: ImageVector? = null
    private var _Flag: ImageVector? = null
    private var _FlagFill: ImageVector? = null
    private var _ClockCounterClockwise: ImageVector? = null
    private var _ClockCounterClockwiseFill: ImageVector? = null
    private var _User: ImageVector? = null
    private var _UserFill: ImageVector? = null
    private var _Plus: ImageVector? = null
    private var _PlusFill: ImageVector? = null
    private var _PlusBold: ImageVector? = null
    private var _ArrowRight: ImageVector? = null
    private var _CheckCircle: ImageVector? = null
    private var _WarningCircle: ImageVector? = null
    private var _XCircle: ImageVector? = null
    private var _ArrowLeft: ImageVector? = null
    private var _Copy: ImageVector? = null
    private var _LockKey: ImageVector? = null
    private var _Wallet: ImageVector? = null
    private var _Trophy: ImageVector? = null
    private var _Check: ImageVector? = null
    private var _CaretRight: ImageVector? = null
    private var _DotsThree: ImageVector? = null
}
