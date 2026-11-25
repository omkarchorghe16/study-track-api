package com.studytrack.api.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small documents built in memory for import tests. */
public final class TestDocuments {
    private TestDocuments() {
    }

    public static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** A Word file: a "Heading 1" paragraph, a bold paragraph, then an embedded PNG. */
    public static byte[] docxWithImage(String heading, String boldText, byte[] png) throws IOException {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Default Extension="png" ContentType="image/png"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>""";
        String packageRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""";
        String documentRels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""";
        String styles = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/></w:style>
                </w:styles>""";
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:v="urn:schemas-microsoft-com:vml">
                <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>%s</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>%s</w:t></w:r></w:p>
                <w:p><w:r><w:pict><v:shape><v:imagedata r:id="rId5"/></v:shape></w:pict></w:r></w:p>
                </w:body>
                </w:document>""".formatted(heading, boldText);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addEntry(zip, "[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "_rels/.rels", packageRels.getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/_rels/document.xml.rels", documentRels.getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/styles.xml", styles.getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/document.xml", document.getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "word/media/image1.png", png);
        }
        return output.toByteArray();
    }

    /** A PDF with one line of text per page. */
    public static byte[] pdfWithPages(List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(72, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
