package io.github.ta3hrj.elm.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.ta3hrj.elm.core.Support
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The QR code in About must read back as the support address, with a white quiet zone for phone cameras. */
class QrCodeTest {
    @Test
    fun `the support QR code reads back as the address`() {
        val matrix = qrMatrix(Support.URL)
        val scale = 4
        val size = matrix.width * scale
        val pixels = IntArray(size * size) { i ->
            if (matrix.get((i % size) / scale, (i / size) / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(Support.URL, QRCodeReader().decode(bitmap).text)
    }

    @Test
    fun `the code has a quiet zone of four modules`() {
        val matrix = qrMatrix(Support.URL)
        for (x in 0 until matrix.width) for (y in 0 until 4) assertFalse(matrix.get(x, y))
    }

    @Test
    fun `the address shows without the scheme`() {
        assertEquals("github.com/sponsors/TA3HRJ", Support.label)
    }
}
