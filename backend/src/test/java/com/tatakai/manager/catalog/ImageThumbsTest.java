package com.tatakai.manager.catalog;

import com.tatakai.manager.catalog.service.rules.ImageThumbs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Miniatura de imagem do catálogo")
class ImageThumbsTest {

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", bos);
        return bos.toByteArray();
    }

    private static BufferedImage read(byte[] b) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    @Test
    @DisplayName("reduz 1000x500 para 256x128 em PNG")
    void downscale() throws Exception {
        var t = ImageThumbs.of(png(1000, 500), "image/png");
        var img = read(t.data());
        assertEquals(256, img.getWidth());
        assertEquals(128, img.getHeight());
        assertEquals("image/png", t.contentType());
    }

    @Test
    @DisplayName("imagem pequena não é ampliada")
    void noUpscale() throws Exception {
        var img = read(ImageThumbs.of(png(100, 50), "image/png").data());
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    @DisplayName("bytes ilegíveis voltam como o original")
    void fallback() {
        byte[] raw = {1, 2, 3};
        var t = ImageThumbs.of(raw, "image/webp");
        assertArrayEquals(raw, t.data());
        assertEquals("image/webp", t.contentType());
    }
}
