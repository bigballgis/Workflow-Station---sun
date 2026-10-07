package com.workflow.email.inbound;

import com.platform.common.mail.ImapTransportProperties;
import com.platform.common.mail.MailDiagnostics;
import com.workflow.email.extract.EmailAttachment;
import com.workflow.email.extract.EmailMessage;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * IMAP implementation of {@link InboundMailClient} using jakarta.mail.
 *
 * <p>Works with app-password mailboxes (QQ / 163 / Outlook / Gmail) — no OAuth required.
 * Incremental polling uses IMAP UIDs as the cursor (Power Automate-style "new email arrives"):
 * a blank cursor returns what was received since the rule was deployed (or seeds a baseline when
 * that instant is unknown) and subsequent polls return only messages with a higher UID. The
 * cursor carries its {@code UIDVALIDITY} (see
 * {@link MailboxCursor}) so a position from a different UID space is reseeded rather than
 * silently matching nothing.
 */
@Slf4j
@Component
public class ImapInboundMailClient implements InboundMailClient {

    @Override
    public FetchResult fetchNew(MailboxAccess access, String folder, String cursor, Instant watchFrom, int max) {
        String protocol = access.ssl() ? "imaps" : "imap";
        return fetchNew(access, new FetchRequest(folder, cursor, watchFrom, max), buildProps(access, protocol));
    }

    record FetchRequest(String folder, String cursor, Instant watchFrom, int max) { }

    FetchResult fetchNew(MailboxAccess access, FetchRequest request, Properties props) {
        String folderName = StringUtils.hasText(request.folder()) ? request.folder() : "INBOX";
        int max = request.max();
        String protocol = access.ssl() ? "imaps" : "imap";
        Session session = Session.getInstance(props);

        log.info("[IMAP-FETCH] begin protocol={} host={} port={} ssl={} user={} mailbox={} authzid={} "
                        + "folder={} cursor={} watchFrom={} max={}",
                protocol, access.host(), access.port(), access.ssl(), mask(access.username()),
                mask(access.mailboxAddress()), authorizationId(props, protocol), folderName, request.cursor(),
                request.watchFrom(), max);

        Store store = null;
        Folder mailFolder = null;
        try {
            store = session.getStore(protocol);
            store.connect(access.host(), access.port(), access.username(), access.password());
            mailFolder = store.getFolder(folderName);
            mailFolder.open(Folder.READ_ONLY);

            UIDFolder uidFolder = (UIDFolder) mailFolder;
            long uidValidity = uidFolder.getUIDValidity();
            long uidNext = uidFolder.getUIDNext();
            MailboxCursor stored = MailboxCursor.parse(request.cursor());

            if (stored == null) {
                return catchUpSince(uidFolder, mailFolder, request.watchFrom(), max, uidValidity)
                        .orElseGet(() -> baseline(access.host(), folderName, uidValidity, uidNext, "first poll"));
            }
            String staleReason = stored.staleReason(uidValidity, uidNext);
            if (staleReason != null) {
                return baseline(access.host(), folderName, uidValidity, uidNext,
                        "stored cursor discarded: " + staleReason);
            }
            FetchResult result = fetchSince(uidFolder, mailFolder, stored.lastUid(), max, uidValidity);
            log.info("[IMAP-FETCH] SUCCESS host={} folder={} fetched={} newCursor={}",
                    access.host(), folderName, result.messages().size(), result.nextCursor());
            return result;
        } catch (Exception e) {
            log.error("[IMAP-FETCH] FAILED host={} port={} ssl={} folder={} | causeChain={} | rootCause={}",
                    access.host(), access.port(), access.ssl(), folderName,
                    MailDiagnostics.causeChain(e), MailDiagnostics.rootCause(e), e);
            throw new IllegalStateException("IMAP fetch failed for " + access.host() + ": "
                    + MailDiagnostics.rootCause(e), e);
        } finally {
            closeQuietly(mailFolder, store);
        }
    }

    /**
     * Baselines mark "start watching from now": history before this point is never replayed,
     * so a discarded cursor loses nothing that the previous mailbox had already processed.
     */
    private FetchResult baseline(
            String host, String folderName, long uidValidity, long uidNext, String reason) {
        long lastUid = Math.max(0, uidNext - 1);
        log.info("[IMAP-FETCH] host={} folder={} {}; baseline cursor={} uidValidity={}",
                host, folderName, reason, lastUid, uidValidity);
        return new FetchResult(List.of(), MailboxCursor.format(uidValidity, lastUid));
    }

    private static String authorizationId(Properties props, String protocol) {
        Object value = props.get("mail." + protocol + ".sasl.authorizationid");
        return value != null ? mask(String.valueOf(value)) : "<none>";
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "<none>";
        }
        int at = value.indexOf('@');
        return at > 1 ? value.charAt(0) + "***" + value.substring(at) : value.charAt(0) + "***";
    }

    private Properties buildProps(MailboxAccess access, String protocol) {
        Properties props = ImapTransportProperties.apply(access.host(), access.port(), access.ssl(), protocol);
        ImapTransportProperties.applyAuthorizationIdentity(
                props, protocol, access.username(), access.mailboxAddress());
        return props;
    }

    /**
     * First poll of a freshly deployed or rebound rule: mail received since {@code watchFrom}
     * belongs to the rule even though the poll comes later. Empty means seed a baseline.
     */
    private Optional<FetchResult> catchUpSince(
            UIDFolder uidFolder, Folder folder, Instant watchFrom, int max, long uidValidity) throws Exception {
        if (watchFrom == null) {
            return Optional.empty();
        }
        // SEARCH SINCE compares calendar days in the server's zone; widen by a day, then
        // apply the instant per message. INTERNALDATE has whole-second precision.
        Date searchFrom = Date.from(watchFrom.minus(1, ChronoUnit.DAYS));
        Instant from = watchFrom.truncatedTo(ChronoUnit.SECONDS);
        List<Message> arrived = new ArrayList<>();
        for (Message message : folder.search(new ReceivedDateTerm(ComparisonTerm.GE, searchFrom))) {
            Date received = message.getReceivedDate();
            if (received != null && !received.toInstant().isBefore(from)) {
                arrived.add(message);
            }
        }
        if (arrived.isEmpty()) {
            return Optional.empty();
        }
        FetchResult result = mapInUidOrder(uidFolder, folder, arrived, max, new MailboxCursor(uidValidity, 0));
        log.info("[IMAP-FETCH] folder={} first poll caught up {} message(s) received since {}; newCursor={}",
                folder.getName(), result.messages().size(), watchFrom, result.nextCursor());
        return Optional.of(result);
    }

    private FetchResult fetchSince(
            UIDFolder uidFolder, Folder folder, long lastUid, int max, long uidValidity) throws Exception {
        Message[] candidates = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
        List<Message> newer = new ArrayList<>();
        for (Message message : candidates) {
            if (message != null && safeUid(uidFolder, message) > lastUid) {
                newer.add(message);
            }
        }
        return mapInUidOrder(uidFolder, folder, newer, max, new MailboxCursor(uidValidity, lastUid));
    }

    /** @param from position before {@code messages}; the returned cursor never moves behind it */
    private FetchResult mapInUidOrder(
            UIDFolder uidFolder, Folder folder, List<Message> messages, int max, MailboxCursor from) {
        messages.sort(Comparator.comparingLong(m -> safeUid(uidFolder, m)));

        List<EmailMessage> mapped = new ArrayList<>();
        long maxUid = from.lastUid();
        for (Message message : messages) {
            if (mapped.size() >= max) {
                break;
            }
            long uid = safeUid(uidFolder, message);
            if (uid == Long.MAX_VALUE) {
                log.error("[IMAP-FETCH] folder={} listed a message whose UID cannot be read; "
                        + "leaving it for the next poll", folder.getName());
                continue;
            }
            EmailMessage email = readOrSkip(message, uid, folder.getName());
            if (email != null) {
                mapped.add(email);
            }
            maxUid = Math.max(maxUid, uid);
        }
        return new FetchResult(mapped, MailboxCursor.format(from.uidValidity(), maxUid));
    }

    /**
     * @return {@code null} when this one message could not be mapped; its UID is still consumed
     */
    private EmailMessage readOrSkip(Message message, long uid, String folderName) {
        try {
            return toEmailMessage(message, uid);
        } catch (Exception e) {
            // FALLBACK(external): a single unreadable message (corrupt MIME, UID expunged between
            // the listing and the read) must not abort the batch — the cursor would never advance
            // past it and every later email would be missed. Losing this one mail is logged at
            // ERROR with its UID so it can be recovered from the mailbox by hand.
            log.error("[IMAP-FETCH] folder={} skipping unreadable message uid={} | rootCause={}",
                    folderName, uid, MailDiagnostics.rootCause(e), e);
            return null;
        }
    }

    private long safeUid(UIDFolder uidFolder, Message message) {
        try {
            return uidFolder.getUID(message);
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    EmailMessage toEmailMessage(Message message, long uid) throws Exception {
        byte[] rawRfc822 = captureRawRfc822(message);
        String subject = message.getSubject();
        String from = (message.getFrom() != null && message.getFrom().length > 0)
                ? formatAddress(message.getFrom()[0]) : null;
        String messageId = resolveMessageId(message, uid);

        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        List<EmailAttachment> attachments = new ArrayList<>();
        extractParts(message, text, html, attachments);

        Map<String, String> headers = new HashMap<>();
        if (from != null) {
            headers.put("from", from);
        }
        putHeader(headers, "to", formatAddresses(message.getRecipients(Message.RecipientType.TO)));
        putHeader(headers, "cc", formatAddresses(message.getRecipients(Message.RecipientType.CC)));
        if (message instanceof MimeMessage mimeMessage) {
            putHeader(headers, "reply-to", formatAddresses(mimeMessage.getReplyTo()));
        }
        if (message.getSentDate() != null) {
            headers.put("date", message.getSentDate().toInstant().toString());
        }
        if (StringUtils.hasText(messageId)) {
            headers.put("message-id", messageId);
        }
        return new EmailMessage(messageId, subject, from,
                text.length() > 0 ? text.toString() : null,
                html.length() > 0 ? html.toString() : null,
                headers,
                attachments,
                rawRfc822);
    }

    /**
     * Best-effort RFC822 copy for optional RAW_EML storage. Capture failure must not
     * abort body/attachment extraction.
     */
    static byte[] captureRawRfc822(Message message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            // FALLBACK(ux): RFC822 capture is optional; missing .eml must not abort
            // body/attachment extract. RAW_EML required is gated later.
            log.warn("Failed to capture RFC822; continuing without raw eml: {}", e.getMessage());
            return new byte[0];
        }
    }

    private static void putHeader(Map<String, String> headers, String name, String value) {
        if (StringUtils.hasText(value)) {
            headers.put(name, value);
        }
    }

    static String formatAddress(Address address) {
        if (address == null) {
            return null;
        }
        if (address instanceof InternetAddress internetAddress) {
            try {
                String email = internetAddress.getAddress();
                String personal = internetAddress.getPersonal();
                if (StringUtils.hasText(personal)) {
                    personal = decodeAddressText(personal);
                }
                if (StringUtils.hasText(personal) && StringUtils.hasText(email)) {
                    return personal + " <" + email + ">";
                }
                if (StringUtils.hasText(email)) {
                    return email;
                }
            } catch (Exception e) {
                // FALLBACK(ux): malformed InternetAddress still rendered via toString decode
                log.debug("InternetAddress formatting fallback: {}", e.getMessage());
            }
        }
        return decodeAddressText(address.toString());
    }

    static String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(formatAddress(addresses[i]));
        }
        return builder.toString();
    }

    static String decodeAddressText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception e) {
            // FALLBACK(ux): undecodable RFC 2047 fragment kept as original header text
            return raw;
        }
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
        extractParts(part, text, html, new ArrayList<>());
    }

    void extractParts(
            Part part,
            StringBuilder text,
            StringBuilder html,
            List<EmailAttachment> attachments) throws Exception {
        if (isInlineCid(part)) {
            return;
        }
        if (isAttachmentPart(part)) {
            collectAttachment(part, attachments);
            return;
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                extractParts(multipart.getBodyPart(i), text, html, attachments);
            }
            return;
        }
        if (content instanceof Message nested) {
            extractParts(nested, text, html, attachments);
            return;
        }
        if (content instanceof InputStream inputStream && part.isMimeType("message/rfc822")) {
            Session nestedSession = Session.getDefaultInstance(new Properties());
            Message nestedMessage = new MimeMessage(nestedSession, inputStream);
            extractParts(nestedMessage, text, html, attachments);
            return;
        }
        appendTextOrHtml(part, content, text, html);
    }

    private static void appendTextOrHtml(
            Part part, Object content, StringBuilder text, StringBuilder html) throws Exception {
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

    /** Inline CID images stay in HTML; they are not FILE attachments. */
    static boolean isInlineCid(Part part) throws Exception {
        String[] cids = part.getHeader("Content-ID");
        if (cids == null || cids.length == 0 || !StringUtils.hasText(cids[0])) {
            return false;
        }
        String disposition = part.getDisposition();
        return !Part.ATTACHMENT.equalsIgnoreCase(disposition);
    }

    static boolean isAttachmentPart(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            return false;
        }
        String disposition = part.getDisposition();
        boolean attached = Part.ATTACHMENT.equalsIgnoreCase(disposition);
        // Nested forwards without disposition stay in the body (#1467); Outlook attached
        // messages are message/rfc822 + attachment and must not be inlined.
        if (part.isMimeType("message/rfc822")) {
            return attached;
        }
        if (attached) {
            return true;
        }
        String filename = decodeFilename(part.getFileName());
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        return !part.isMimeType("text/plain") && !part.isMimeType("text/html");
    }

    private static void collectAttachment(Part part, List<EmailAttachment> attachments) throws Exception {
        String filename = decodeFilename(part.getFileName());
        if (!StringUtils.hasText(filename)) {
            filename = part.isMimeType("message/rfc822") ? "message.eml" : "attachment";
        }
        byte[] bytes = readPartBytes(part);
        if (bytes.length == 0) {
            return;
        }
        attachments.add(new EmailAttachment(filename, part.getContentType(), bytes));
    }

    private static byte[] readPartBytes(Part part) throws Exception {
        try (InputStream in = part.getInputStream()) {
            return in.readAllBytes();
        } catch (Exception streamFailed) {
            Object content = part.getContent();
            if (content instanceof byte[] raw) {
                return raw;
            }
            if (content instanceof InputStream in) {
                return in.readAllBytes();
            }
            throw streamFailed;
        }
    }

    static String decodeFilename(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        try {
            return MimeUtility.decodeText(raw);
        } catch (Exception e) {
            return raw;
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
