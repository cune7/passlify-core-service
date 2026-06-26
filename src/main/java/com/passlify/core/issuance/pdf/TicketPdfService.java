package com.passlify.core.issuance.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.passlify.core.event.Event;
import com.passlify.core.event.Location;
import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.qr.QrImageService;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Renders a single-ticket PDF (OpenPDF) with the event details and the QR code. */
@Service
public class TicketPdfService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Belgrade");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private final QrImageService qrImageService;

    public TicketPdfService(QrImageService qrImageService) {
        this.qrImageService = qrImageService;
    }

    public byte[] render(Ticket ticket) {
        Event event = ticket.getEvent();
        Document doc = new Document(PageSize.A6, 24, 24, 24, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(paragraph("PASSLIFY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10), Element.ALIGN_LEFT));
            doc.add(paragraph(event.getName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16), Element.ALIGN_LEFT));
            doc.add(paragraph(DATE_FMT.format(event.getStartsAt().atZone(DISPLAY_ZONE)),
                    bodyFont(), Element.ALIGN_LEFT));
            doc.add(paragraph(locationLine(event.getLocation()), bodyFont(), Element.ALIGN_LEFT));
            doc.add(paragraph(" ", bodyFont(), Element.ALIGN_LEFT));
            doc.add(paragraph("Ticket: " + ticket.getTicketType().getName(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12), Element.ALIGN_LEFT));
            if (ticket.getAttendeeName() != null) {
                doc.add(paragraph("Attendee: " + ticket.getAttendeeName(), bodyFont(), Element.ALIGN_LEFT));
            }

            Image qr = Image.getInstance(qrImageService.pngFor(ticket.getQrToken()));
            qr.scaleToFit(180, 180);
            qr.setAlignment(Element.ALIGN_CENTER);
            doc.add(qr);

            doc.add(paragraph(ticket.getSerialNumber(),
                    FontFactory.getFont(FontFactory.COURIER, 11), Element.ALIGN_CENTER));
            doc.add(paragraph("Present this QR code at the entrance.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8), Element.ALIGN_CENTER));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render ticket PDF", e);
        }
    }

    private static Font bodyFont() {
        return FontFactory.getFont(FontFactory.HELVETICA, 10);
    }

    private static Paragraph paragraph(String text, Font font, int alignment) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(alignment);
        return p;
    }

    private static String locationLine(Location loc) {
        if (loc == null) {
            return "";
        }
        return loc.getVenueName() + ", " + loc.getCity();
    }
}
