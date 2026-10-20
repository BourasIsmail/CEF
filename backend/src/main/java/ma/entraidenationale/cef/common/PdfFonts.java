package ma.entraidenationale.cef.common;

import java.io.IOException;
import java.io.InputStream;

import org.openpdf.text.DocumentException;
import org.openpdf.text.pdf.BaseFont;

/** Police arabe embarquée dans les PDF (Noto Naskh Arabic, licence SIL OFL : voir resources/fonts), chargée une seule fois. */
public final class PdfFonts {

    private static volatile BaseFont regular;
    private static volatile BaseFont bold;

    private PdfFonts() {
    }

    public static BaseFont regular() {
        if (regular == null) {
            synchronized (PdfFonts.class) {
                if (regular == null) {
                    regular = load("/fonts/NotoNaskhArabic_400Regular.ttf", "NotoNaskhArabic-Regular.ttf");
                }
            }
        }
        return regular;
    }

    public static BaseFont bold() {
        if (bold == null) {
            synchronized (PdfFonts.class) {
                if (bold == null) {
                    bold = load("/fonts/NotoNaskhArabic_700Bold.ttf", "NotoNaskhArabic-Bold.ttf");
                }
            }
        }
        return bold;
    }

    private static BaseFont load(String resource, String name) {
        try (InputStream in = PdfFonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Police introuvable : " + resource);
            }
            return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Impossible de charger la police " + resource, e);
        }
    }
}
