package com.eaglepoint.venue.mapper;

import com.eaglepoint.venue.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Rollback
class MapperIntegrationTest {

    @Autowired private UserAccountMapper userAccountMapper;
    @Autowired private AuthSessionMapper authSessionMapper;
    @Autowired private TicketOrderMapper ticketOrderMapper;
    @Autowired private TicketReservationMapper ticketReservationMapper;
    @Autowired private PublishedContentMapper publishedContentMapper;
    @Autowired private ContentVersionMapper contentVersionMapper;
    @Autowired private ContentAuditLogMapper contentAuditLogMapper;
    @Autowired private PaymentTransactionMapper paymentTransactionMapper;
    @Autowired private SettlementCallbackMapper settlementCallbackMapper;
    @Autowired private RefundTransactionMapper refundTransactionMapper;
    @Autowired private ManagedDocumentMapper managedDocumentMapper;
    @Autowired private ManagedDocumentVersionMapper managedDocumentVersionMapper;
    @Autowired private ManagedDownloadLinkMapper managedDownloadLinkMapper;

    // ---------------------------------------------------------------------------
    // Helper builders
    // ---------------------------------------------------------------------------

    private UserAccount buildUser(String username) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash("$2a$10$testHashValue");
        user.setRole("SENIOR");
        user.setActive("Y");
        user.setFailedAttempts(0);
        return user;
    }

    private TicketOrder buildOrder(String orderCode) {
        TicketOrder order = new TicketOrder();
        order.setEventId(1L);
        order.setSessionId(1L);
        order.setTicketTypeId(1L);
        order.setOrderCode(orderCode);
        order.setBuyerReference("test_buyer");
        order.setChannel("ONLINE_PORTAL");
        order.setQuantity(1);
        order.setStatus("UNPAID");
        order.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15));
        order.setCancelExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setInventoryReturned(0);
        return order;
    }

    private TicketReservation buildReservation(String reservationCode) {
        TicketReservation res = new TicketReservation();
        res.setTicketTypeId(1L);
        res.setReservationCode(reservationCode);
        res.setBuyerReference("test_buyer");
        res.setChannel("ONLINE_PORTAL");
        res.setQuantity(1);
        res.setUnitPrice(new BigDecimal("45.00"));
        res.setTotalAmount(new BigDecimal("45.00"));
        res.setStatus("CONFIRMED");
        return res;
    }

    private PublishedContent buildContent(String title, String createdBy) {
        PublishedContent content = new PublishedContent();
        content.setTitle(title);
        content.setBody("Body text for " + title);
        content.setState("DRAFT");
        content.setCurrentVersion(1);
        content.setCreatedBy(createdBy);
        return content;
    }

    private PaymentTransaction buildPayment(String transactionRef) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionRef(transactionRef);
        tx.setTenderType("TERMINAL_BATCH");
        tx.setGrossAmount(new BigDecimal("100.00"));
        tx.setRefundedAmount(BigDecimal.ZERO);
        tx.setMerchantCode("MERCHANT001");
        tx.setStatus("RECORDED");
        return tx;
    }

    private ManagedDocument buildDocument(String title) {
        ManagedDocument doc = new ManagedDocument();
        doc.setFolderId(1L);   // seed: /waivers folder
        doc.setTitle(title);
        doc.setAccessLevel("STAFF_AND_ADMIN");
        doc.setCreatedBy("mapper_test_user");
        return doc;
    }

    private ManagedDocumentVersion buildDocumentVersion(Long documentId) {
        ManagedDocumentVersion ver = new ManagedDocumentVersion();
        ver.setDocumentId(documentId);
        ver.setVersionNumber(1);
        ver.setStoredPath("/waivers/doc-" + documentId + "/v1.pdf");
        ver.setOriginalFileName("waiver.pdf");
        ver.setContentType("application/pdf");
        ver.setFileSize(1024L);
        ver.setChecksum("abc123checksum");
        ver.setUploadedBy("mapper_test_user");
        return ver;
    }

    // ---------------------------------------------------------------------------
    // Test 1 – UserAccount: insert and findByUsername
    // ---------------------------------------------------------------------------

    @Test
    void userAccount_insertAndFindByUsername() {
        String username = "mapper_test_" + System.currentTimeMillis();
        UserAccount user = buildUser(username);
        int rows = userAccountMapper.insert(user);

        assertEquals(1, rows);
        assertNotNull(user.getId(), "auto-generated id must be set after insert");

        UserAccount found = userAccountMapper.findByUsername(username);
        assertNotNull(found);
        assertEquals(username, found.getUsername());
        assertEquals("$2a$10$testHashValue", found.getPasswordHash());
        assertEquals("SENIOR", found.getRole());
        assertEquals("Y", found.getActive());
        assertEquals(0, found.getFailedAttempts());
        assertNull(found.getLockoutUntil());
    }

    // ---------------------------------------------------------------------------
    // Test 2 – UserAccount: unique constraint on username
    // ---------------------------------------------------------------------------

    @Test
    void userAccount_uniqueConstraintOnUsername() {
        String username = "mapper_dup_" + System.currentTimeMillis();
        userAccountMapper.insert(buildUser(username));

        assertThrows(Exception.class, () -> userAccountMapper.insert(buildUser(username)));
    }

    // ---------------------------------------------------------------------------
    // Test 3 – UserAccount: updateLoginFailure increments attempts and sets lockout
    // ---------------------------------------------------------------------------

    @Test
    void userAccount_updateLoginFailure() {
        String username = "mapper_lock_" + System.currentTimeMillis();
        UserAccount user = buildUser(username);
        userAccountMapper.insert(user);

        LocalDateTime lockoutUntil = LocalDateTime.now().plusMinutes(30);
        int updated = userAccountMapper.updateLoginFailure(user.getId(), 3, lockoutUntil);

        assertEquals(1, updated);

        UserAccount found = userAccountMapper.findById(user.getId());
        assertNotNull(found);
        assertEquals(3, found.getFailedAttempts());
        assertNotNull(found.getLockoutUntil());
    }

    // ---------------------------------------------------------------------------
    // Test 4 – AuthSession: insert and findValidByToken
    // ---------------------------------------------------------------------------

    @Test
    void authSession_insertAndFindByToken() {
        String username = "mapper_session_" + System.currentTimeMillis();
        UserAccount user = buildUser(username);
        userAccountMapper.insert(user);

        String token = "tok-" + System.currentTimeMillis();
        AuthSession session = new AuthSession();
        session.setUserId(user.getId());
        session.setToken(token);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));

        int rows = authSessionMapper.insert(session);
        assertEquals(1, rows);

        AuthSession found = authSessionMapper.findValidByToken(token, LocalDateTime.now());
        assertNotNull(found);
        assertEquals(user.getId(), found.getUserId());
        assertEquals(token, found.getToken());
    }

    // ---------------------------------------------------------------------------
    // Test 5 – TicketOrder: insert and findById
    // ---------------------------------------------------------------------------

    @Test
    void ticketOrder_insertAndFindById() {
        String orderCode = "MAPPER-" + System.currentTimeMillis();
        TicketOrder order = buildOrder(orderCode);
        int rows = ticketOrderMapper.insert(order);

        assertEquals(1, rows);
        assertNotNull(order.getId());

        TicketOrder found = ticketOrderMapper.findById(order.getId());
        assertNotNull(found);
        assertEquals(orderCode, found.getOrderCode());
        assertEquals(1L, found.getEventId());
        assertEquals(1L, found.getSessionId());
        assertEquals(1L, found.getTicketTypeId());
        assertEquals("UNPAID", found.getStatus());
        assertEquals(1, found.getQuantity());
    }

    // ---------------------------------------------------------------------------
    // Test 6 – TicketOrder: markPaid changes status to PAID
    // ---------------------------------------------------------------------------

    @Test
    void ticketOrder_markPaid() {
        String orderCode = "MAPPER-PAY-" + System.currentTimeMillis();
        TicketOrder order = buildOrder(orderCode);
        ticketOrderMapper.insert(order);

        int updated = ticketOrderMapper.markPaid(order.getId());
        assertEquals(1, updated);

        TicketOrder found = ticketOrderMapper.findById(order.getId());
        assertNotNull(found);
        assertEquals("PAID", found.getStatus());
    }

    // ---------------------------------------------------------------------------
    // Test 7 – TicketOrder: unique constraint on orderCode
    // ---------------------------------------------------------------------------

    @Test
    void ticketOrder_uniqueOrderCode() {
        String orderCode = "MAPPER-DUP-" + System.currentTimeMillis();
        ticketOrderMapper.insert(buildOrder(orderCode));

        assertThrows(Exception.class, () -> ticketOrderMapper.insert(buildOrder(orderCode)));
    }

    // ---------------------------------------------------------------------------
    // Test 8 – TicketReservation: insert and verify via updateStatus round-trip
    // ---------------------------------------------------------------------------

    @Test
    void ticketReservation_insertAndFindById() {
        String code = "MRES-" + System.currentTimeMillis();
        TicketReservation res = buildReservation(code);
        int rows = ticketReservationMapper.insert(res);

        assertEquals(1, rows);
        assertNotNull(res.getId(), "auto-generated id must be set after insert");

        // TicketReservationMapper has no findById; verify round-trip through updateStatus
        int updated = ticketReservationMapper.updateStatus(res.getId(), "CANCELLED");
        assertEquals(1, updated, "updateStatus should affect exactly one row");
    }

    // ---------------------------------------------------------------------------
    // Test 9 – TicketReservation: unique constraint on reservationCode
    // ---------------------------------------------------------------------------

    @Test
    void ticketReservation_uniqueReservationCode() {
        String code = "MRES-DUP-" + System.currentTimeMillis();
        ticketReservationMapper.insert(buildReservation(code));

        assertThrows(Exception.class, () -> ticketReservationMapper.insert(buildReservation(code)));
    }

    // ---------------------------------------------------------------------------
    // Test 10 – PublishedContent: insert and findById
    // ---------------------------------------------------------------------------

    @Test
    void publishedContent_insertAndFindById() {
        PublishedContent content = buildContent("Mapper Test Content", "author_mapper");
        int rows = publishedContentMapper.insert(content);

        assertEquals(1, rows);
        assertNotNull(content.getId());

        PublishedContent found = publishedContentMapper.findById(content.getId());
        assertNotNull(found);
        assertEquals("Mapper Test Content", found.getTitle());
        assertEquals("DRAFT", found.getState());
        assertEquals("author_mapper", found.getCreatedBy());
        assertEquals(1, found.getCurrentVersion());
        assertNull(found.getPublishedAt());
    }

    // ---------------------------------------------------------------------------
    // Test 11 – PublishedContent: findByCreatedBy returns only matching records
    // ---------------------------------------------------------------------------

    @Test
    void publishedContent_findByCreatedBy() {
        String authorA = "author_a_" + System.currentTimeMillis();
        String authorB = "author_b_" + System.currentTimeMillis();

        publishedContentMapper.insert(buildContent("Content by A", authorA));
        publishedContentMapper.insert(buildContent("Content by B", authorB));

        List<PublishedContent> resultsA = publishedContentMapper.findByCreatedBy(authorA);
        assertNotNull(resultsA);
        assertEquals(1, resultsA.size());
        assertEquals(authorA, resultsA.get(0).getCreatedBy());

        List<PublishedContent> resultsB = publishedContentMapper.findByCreatedBy(authorB);
        assertNotNull(resultsB);
        assertEquals(1, resultsB.size());
        assertEquals(authorB, resultsB.get(0).getCreatedBy());
    }

    // ---------------------------------------------------------------------------
    // Test 12 – PublishedContent: updateState changes state to PUBLISH
    // ---------------------------------------------------------------------------

    @Test
    void publishedContent_updateState() {
        PublishedContent content = buildContent("State Change Test", "author_state");
        publishedContentMapper.insert(content);

        LocalDateTime now = LocalDateTime.now();
        int updated = publishedContentMapper.updateState(content.getId(), "PUBLISH", now, now);
        assertEquals(1, updated);

        PublishedContent found = publishedContentMapper.findById(content.getId());
        assertNotNull(found);
        assertEquals("PUBLISH", found.getState());
        assertNotNull(found.getPublishedAt());
    }

    // ---------------------------------------------------------------------------
    // Test 13 – ContentVersion: insert two versions and findByContentId in order
    // ---------------------------------------------------------------------------

    @Test
    void contentVersion_insertAndFindByContentId() {
        PublishedContent content = buildContent("Versioned Content", "author_ver");
        publishedContentMapper.insert(content);

        ContentVersion v1 = new ContentVersion();
        v1.setContentId(content.getId());
        v1.setVersionNumber(1);
        v1.setTitle("Version 1 Title");
        v1.setBody("Body of version 1");
        v1.setChangedBy("author_ver");
        v1.setChangeType("CREATE");
        v1.setChangeSummary("Initial creation");
        contentVersionMapper.insert(v1);

        ContentVersion v2 = new ContentVersion();
        v2.setContentId(content.getId());
        v2.setVersionNumber(2);
        v2.setTitle("Version 2 Title");
        v2.setBody("Body of version 2");
        v2.setChangedBy("author_ver");
        v2.setChangeType("UPDATE");
        v2.setChangeSummary("First revision");
        contentVersionMapper.insert(v2);

        List<ContentVersion> versions = contentVersionMapper.findByContentId(content.getId());
        assertNotNull(versions);
        assertEquals(2, versions.size());

        // Verify ordering: version 1 before version 2
        assertTrue(versions.get(0).getVersionNumber() < versions.get(1).getVersionNumber(),
                "Versions should be ordered ascending by versionNumber");
    }

    // ---------------------------------------------------------------------------
    // Test 14 – ContentAuditLog: insert and findByContentId
    // ---------------------------------------------------------------------------

    @Test
    void contentAuditLog_insertAndFindByContentId() {
        PublishedContent content = buildContent("Audit Log Content", "author_audit");
        publishedContentMapper.insert(content);

        ContentAuditLog log = new ContentAuditLog();
        log.setContentId(content.getId());
        log.setVersionId(null);
        log.setAction("CREATE_DRAFT");
        log.setChangedBy("author_audit");
        log.setChangeDetail("Initial draft created via mapper test");
        contentAuditLogMapper.insert(log);

        List<ContentAuditLog> logs = contentAuditLogMapper.findByContentId(content.getId());
        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals("CREATE_DRAFT", logs.get(0).getAction());
        assertEquals("author_audit", logs.get(0).getChangedBy());
        assertEquals(content.getId(), logs.get(0).getContentId());
    }

    // ---------------------------------------------------------------------------
    // Test 15 – PaymentTransaction: insert and findByTransactionRef
    // ---------------------------------------------------------------------------

    @Test
    void paymentTransaction_insertAndFindByRef() {
        String ref = "PTX-" + System.currentTimeMillis();
        PaymentTransaction tx = buildPayment(ref);
        int rows = paymentTransactionMapper.insert(tx);

        assertEquals(1, rows);
        assertNotNull(tx.getId());

        PaymentTransaction found = paymentTransactionMapper.findByTransactionRef(ref);
        assertNotNull(found);
        assertEquals(ref, found.getTransactionRef());
        assertEquals("TERMINAL_BATCH", found.getTenderType());
        assertEquals(0, new BigDecimal("100.00").compareTo(found.getGrossAmount()));
        assertEquals("MERCHANT001", found.getMerchantCode());
        assertEquals("RECORDED", found.getStatus());
    }

    // ---------------------------------------------------------------------------
    // Test 16 – PaymentTransaction: unique constraint on transactionRef
    // ---------------------------------------------------------------------------

    @Test
    void paymentTransaction_uniqueTransactionRef() {
        String ref = "PTX-DUP-" + System.currentTimeMillis();
        paymentTransactionMapper.insert(buildPayment(ref));

        assertThrows(Exception.class, () -> paymentTransactionMapper.insert(buildPayment(ref)));
    }

    // ---------------------------------------------------------------------------
    // Test 17 – SettlementCallback: insert and findByTransactionRef
    // ---------------------------------------------------------------------------

    @Test
    void settlementCallback_insertAndFindByRef() {
        String ref = "SCB-" + System.currentTimeMillis();
        SettlementCallback cb = new SettlementCallback();
        cb.setTransactionRef(ref);
        cb.setGatewayBatchRef("BATCH-001");
        cb.setSettledAmount(new BigDecimal("98.50"));
        cb.setCallbackStatus("MATCHED");
        cb.setSource("GATEWAY");
        cb.setCallbackAt(LocalDateTime.now());

        int rows = settlementCallbackMapper.insert(cb);
        assertEquals(1, rows);
        assertNotNull(cb.getId());

        SettlementCallback found = settlementCallbackMapper.findByTransactionRef(ref);
        assertNotNull(found);
        assertEquals(ref, found.getTransactionRef());
        assertEquals("BATCH-001", found.getGatewayBatchRef());
        assertEquals(0, new BigDecimal("98.50").compareTo(found.getSettledAmount()));
        assertEquals("MATCHED", found.getCallbackStatus());
        assertEquals("GATEWAY", found.getSource());
    }

    // ---------------------------------------------------------------------------
    // Test 18 – SettlementCallback: unique constraint on transactionRef
    // ---------------------------------------------------------------------------

    @Test
    void settlementCallback_uniqueTransactionRef() {
        String ref = "SCB-DUP-" + System.currentTimeMillis();
        SettlementCallback cb = new SettlementCallback();
        cb.setTransactionRef(ref);
        cb.setSettledAmount(new BigDecimal("50.00"));
        cb.setCallbackStatus("MATCHED");
        cb.setSource("GATEWAY");
        cb.setCallbackAt(LocalDateTime.now());
        settlementCallbackMapper.insert(cb);

        assertThrows(Exception.class, () -> {
            SettlementCallback dup = new SettlementCallback();
            dup.setTransactionRef(ref);
            dup.setSettledAmount(new BigDecimal("50.00"));
            dup.setCallbackStatus("MATCHED");
            dup.setSource("GATEWAY");
            dup.setCallbackAt(LocalDateTime.now());
            settlementCallbackMapper.insert(dup);
        });
    }

    // ---------------------------------------------------------------------------
    // Test 19 – RefundTransaction: insert and find by paymentId via findAll
    // ---------------------------------------------------------------------------

    @Test
    void refundTransaction_insertAndFindByPaymentId() {
        String payRef = "PTX-REF-" + System.currentTimeMillis();
        PaymentTransaction payment = buildPayment(payRef);
        paymentTransactionMapper.insert(payment);

        RefundTransaction refund = new RefundTransaction();
        refund.setPaymentId(payment.getId());
        refund.setTransactionRef("RFD-" + System.currentTimeMillis());
        refund.setRefundAmount(new BigDecimal("25.00"));
        refund.setRefundType("PARTIAL");
        refund.setReason("Customer request");

        int rows = refundTransactionMapper.insert(refund);
        assertEquals(1, rows);
        assertNotNull(refund.getId());

        // RefundTransactionMapper only exposes findAll; locate the inserted row by id
        List<RefundTransaction> all = refundTransactionMapper.findAll();
        assertNotNull(all);
        RefundTransaction found = all.stream()
                .filter(r -> r.getId().equals(refund.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(found, "inserted refund must appear in findAll");
        assertEquals(payment.getId(), found.getPaymentId());
        assertEquals(0, new BigDecimal("25.00").compareTo(found.getRefundAmount()));
        assertEquals("PARTIAL", found.getRefundType());
        assertEquals("Customer request", found.getReason());
    }

    // ---------------------------------------------------------------------------
    // Test 20 – ManagedDocument: insert and findById
    // ---------------------------------------------------------------------------

    @Test
    void managedDocument_insertAndFindById() {
        String title = "Waiver Form " + System.currentTimeMillis();
        ManagedDocument doc = buildDocument(title);
        int rows = managedDocumentMapper.insert(doc);

        assertEquals(1, rows);
        assertNotNull(doc.getId());

        ManagedDocument found = managedDocumentMapper.findById(doc.getId());
        assertNotNull(found);
        assertEquals(title, found.getTitle());
        assertEquals(1L, found.getFolderId());
        assertEquals("STAFF_AND_ADMIN", found.getAccessLevel());
        assertEquals("mapper_test_user", found.getCreatedBy());
    }

    // ---------------------------------------------------------------------------
    // Test 21 – ManagedDownloadLink: insert and findValidByToken
    // ---------------------------------------------------------------------------

    @Test
    void managedDownloadLink_insertAndFindByToken() {
        // Insert a document first
        ManagedDocument doc = buildDocument("Link Test Doc " + System.currentTimeMillis());
        managedDocumentMapper.insert(doc);

        // Insert a document version (required FK for managed_download_link)
        ManagedDocumentVersion ver = buildDocumentVersion(doc.getId());
        managedDocumentVersionMapper.insert(ver);
        assertNotNull(ver.getId());

        // Insert the download link
        String token = "dltoken-" + System.currentTimeMillis();
        ManagedDownloadLink link = new ManagedDownloadLink();
        link.setDocumentId(doc.getId());
        link.setVersionId(ver.getId());
        link.setToken(token);
        link.setExpiresAt(LocalDateTime.now().plusHours(24));
        link.setCreatedBy("mapper_test_user");

        int rows = managedDownloadLinkMapper.insert(link);
        assertEquals(1, rows);
        assertNotNull(link.getId());

        // findValidByToken uses now to check expiry — pass a time before expiresAt
        ManagedDownloadLink found = managedDownloadLinkMapper.findValidByToken(token, LocalDateTime.now());
        assertNotNull(found);
        assertEquals(doc.getId(), found.getDocumentId());
        assertEquals(ver.getId(), found.getVersionId());
        assertEquals(token, found.getToken());
    }
}
