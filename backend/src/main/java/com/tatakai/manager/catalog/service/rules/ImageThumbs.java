package com.tatakai.manager.catalog.service.rules;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Miniatura em PNG com lado maior de até 256 px (puro, sem dependência). */
public final class ImageThumbs {

    public static final int MAX_SIDE = 256;

    /** Miniatura: bytes + tipo de conteúdo. */
    public record Thumb(byte[] data, String contentType) {}

    private ImageThumbs() {}

    /** Se a imagem não puder ser lida (WebP, SVG...), devolve o original. Nunca amplia. */
    public static Thumb of(byte[] original, String contentType) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return new Thumb(original, contentType);
            int w = src.getWidth(), h = src.getHeight();
            double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
            BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src.getScaledInstance(tw, th, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return new Thumb(bos.toByteArray(), "image/png");
        } catch (Exception e) {
            return new Thumb(original, contentType);
        }
    }
}
