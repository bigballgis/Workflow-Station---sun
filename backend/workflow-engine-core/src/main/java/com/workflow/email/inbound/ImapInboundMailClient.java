package com.workflow.email.inbound;

import com.workflow.email.extract.EmailMessage;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * IMAP implementation of {@link InboundMailClient} using jakarta.mail.
 *
 * <p>Works with app-password mailboxes (QQ / 163 / Outlook / Gmail) — no OAuth required.
 * Incremental polling uses IMAP UIDs as the cursor (Power Automate-style "new email arrives"):
 * a blank cursor seeds the baseline (no history replay) and subsequent polls return only
 * messages with a higher UID.
 */
@Slf4j
@Component
public class ImapInboundMailClient implements InboundMailClient {

    @Override
    public FetchResult fetchNew(MailboxAccess access, String folder, String cursor, int max) {
        String folderName = StringUtils.hasText(folder) ? folder : "INBOX";
        String protocol = access.ssl() ? "imaps" : "imap";
        Session session = Session.getInstance(buildProps(access, protocol));

        Store store = null;
        Folder mailFolder = null;
        try {
            store = session.getStore(protocol);
            store.connect(access.host(), access.port(), access.username(), access.password());
            mailFolder = store.getFolder(folderName);
            mailFolder.open(Folder.READ_ONLY);

            UIDFolder uidFolder = (UIDFolder) mailFolder;
            long lastUid = parseCursor(cursor);

            if (lastUid < 0) {
                long baseline = Math.max(0, uidFolder.getUIDNext() - 1);
                return new FetchResult(List.of(), String.valueOf(baseline));
            }
            return fetchSince(uidFolder, mailFolder, lastUid, max);
        } catch (Exception e) {
            throw new IllegalStateException("IMAP fetch failed for " + access.host() + ": " + e.getMessage(), e);
        } finally {
            closeQuietly(mailFolder, store);
        }
    }

    private Properties buildProps(MailboxAccess access, String protocol) {
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", access.host());
        props.put("mail." + protocol + ".port", String.valueOf(access.port()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(access.ssl()));
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "20000");
        return props;
    }

    private FetchResult fetchSince(UIDFolder uidFolder, Folder folder, long lastUid, int max) throws Exception {
        Message[] candidates = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
        List<Message> newer = new ArrayList<>();
        for (Message message : candidates) {
            if (uidFolder.getUID(message) > lastUid) {
                newer.add(message);
            }
        }
        newer.sort(Comparator.comparingLong(m -> safeUid(uidFolder, m)));

        List<EmailMessage> mapped = new ArrayList<>();
        long maxUid = lastUid;
        for (Message message : newer) {
            if (mapped.size() >= max) {
                break;
            }
            long uid = uidFolder.getUID(message);
            mapped.add(toEmailMessage(message, uid));
            maxUid = Math.max(maxUid, uid);
        }
        return new FetchResult(mapped, String.valueOf(maxUid));
    }

    private long safeUid(UIDFolder uidFolder, Message message) {
        try {
            return uidFolder.getUID(message);
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private EmailMessage toEmailMessage(Message message, long uid) throws Exception {
        String subject = message.getSubject();
        String from = (message.getFrom() != null && message.getFrom().length > 0)
                ? message.getFrom()[0].toString() : null;
        String messageId = resolveMessageId(message, uid);

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        extractParts(message, text, html);

        Map<String, String> headers = new HashMap<>();
        if (from != null) {
            headers.put("from", from);
        }
        if (message.getSentDate() != null) {
            headers.put("date", message.getSentDate().toInstant().toString());
        }
        return new EmailMessage(messageId, subject, from,
                text.length() > 0 ? text.toString() : null,
                html.length() > 0 ? html.toString() : null,
                headers);
    }

    private String resolveMessageId(Message message, long uid) throws Exception {
        if (message instanceof MimeMessage mime) {
            String id = mime.getMessageID();
            if (StringUtils.hasText(id)) {
                return id;
            }
        }
        return "imap-uid:" + uid;
    }

    /** Recursively collects text/plain and text/html bodies from a (possibly multipart) part. */
    void extractParts(Part part, StringBuilder text, StringBuilder html) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                extractParts(multipart.getBodyPart(i), text, html);
            }
            return;
        }
        if (content instanceof Message nested) {
            extractParts(nested, text, html);
            return;
        }
        if (content instanceof InputStream inputStream && part.isMimeType("message/rfc822")) {
            Session nestedSession = Session.getDefaultInstance(new Properties());
            Message nestedMessage = new MimeMessage(nestedSession, inputStream);
            extractParts(nestedMessage, text, html);
            return;
        }
        String body = contentAsString(content);
        if (body == null) {
            return;
        }
        if (part.isMimeType("text/html")) {
            html.append(body);
        } else if (part.isMimeType("text/plain")) {
            text.append(body);
        }
    }

    private static String contentAsString(Object content) throws Exception {
        if (content instanceof String body) {
            return body;
        }
        if (content instanceof InputStream inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return null;
    }

    private long parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return -1L;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private void closeQuietly(Folder folder, Store store) {
        try {
            if (folder != null && folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception e) {
            log.debug("IMAP folder close ignored: {}", e.getMessage());
        }
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (Exception e) {
            log.debug("IMAP store close ignored: {}", e.getMessage());
        }
    }
}
