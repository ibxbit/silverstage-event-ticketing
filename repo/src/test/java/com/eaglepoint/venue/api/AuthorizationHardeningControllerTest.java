package com.eaglepoint.venue.api;

import com.eaglepoint.venue.api.dto.ContentResponse;
import com.eaglepoint.venue.api.dto.CreateSeatOrderRequest;
import com.eaglepoint.venue.api.dto.DiffResponse;
import com.eaglepoint.venue.api.dto.ReservationResponse;
import com.eaglepoint.venue.api.dto.SeatOrderResponse;
import com.eaglepoint.venue.domain.UserAccount;
import com.eaglepoint.venue.service.AccountSecurityService;
import com.eaglepoint.venue.service.FileManagementService;
import com.eaglepoint.venue.service.ModerationService;
import com.eaglepoint.venue.service.PaymentReconciliationService;
import com.eaglepoint.venue.service.PublishingWorkflowService;
import com.eaglepoint.venue.service.RequestAuthorizationService;
import com.eaglepoint.venue.service.SeatReservationService;
import com.eaglepoint.venue.service.TicketingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthorizationHardeningControllerTest {

    @Mock
    private AccountSecurityService accountSecurityService;
    @Mock
    private ModerationService moderationService;
    @Mock
    private FileManagementService fileManagementService;
    @Mock
    private PublishingWorkflowService publishingWorkflowService;
    @Mock
    private SeatReservationService seatReservationService;
    @Mock
    private TicketingService ticketingService;
    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RequestAuthorizationService requestAuthorizationService = new RequestAuthorizationService(accountSecurityService);
        ModerationController moderationController = new ModerationController(moderationService, requestAuthorizationService);
        FileManagementController fileManagementController = new FileManagementController(fileManagementService, requestAuthorizationService);
        PublishingWorkflowController publishingWorkflowController = new PublishingWorkflowController(publishingWorkflowService, requestAuthorizationService);
        SeatReservationController seatReservationController = new SeatReservationController(seatReservationService, requestAuthorizationService);
        TicketingController ticketingController = new TicketingController(ticketingService, requestAuthorizationService);
        PaymentController paymentController = new PaymentController(paymentReconciliationService, requestAuthorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(moderationController, fileManagementController, publishingWorkflowController, seatReservationController, ticketingController, paymentController).build();
    }

    @Test
    void privilegedEndpoint_withoutToken_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ok.txt", "text/plain", "payload".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("title", "Doc")
                        .param("folderPath", "/ops")
                        .param("accessLevel", "STAFF_AND_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedRoleHeader_doesNotBypassTokenRoleChecks() throws Exception {
        UserAccount senior = new UserAccount();
        senior.setUsername("senior_user");
        senior.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-senior")).thenReturn(senior);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "SERVICE_STAFF", "ORG_ADMIN", "PLATFORM_ADMIN");

        MockMultipartFile file = new MockMultipartFile("file", "ok.txt", "text/plain", "payload".getBytes());
        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("title", "Doc")
                        .param("folderPath", "/ops")
                        .param("accessLevel", "STAFF_AND_ADMIN")
                        .header("X-Auth-Token", "token-senior")
                        .header("X-User-Role", "ORG_ADMIN"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fileManagementService);
    }

    @Test
    void objectLevelAuth_userCannotReadAnotherUsersNotifications() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-user-a")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "MODERATOR", "ORG_ADMIN", "PLATFORM_ADMIN");

        mockMvc.perform(get("/api/moderation/users/user_b/notifications")
                        .header("X-Auth-Token", "token-user-a"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(moderationService);
    }

    @Test
    void objectLevelAuth_selfAccessAllowedForNotificationsAndPenalties() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-user-a")).thenReturn(user);
        when(moderationService.notifications("user_a")).thenReturn(Collections.emptyList());
        when(moderationService.penaltyStatus("user_a")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/moderation/users/user_a/notifications")
                        .header("X-Auth-Token", "token-user-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/moderation/users/user_a/penalties")
                        .header("X-Auth-Token", "token-user-a"))
                .andExpect(status().isOk());
    }

    @Test
    void publishingPrivilegedTransition_requiresPrivilegedTokenRole() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-user-a")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "MODERATOR", "ORG_ADMIN", "PLATFORM_ADMIN");

        mockMvc.perform(post("/api/publishing/content/100/review")
                        .header("X-Auth-Token", "token-user-a")
                        .header("X-User-Role", "MODERATOR"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(publishingWorkflowService);
    }

    @Test
    void publishingPrivilegedTransition_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/publishing/content/100/review"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seatOrderCreate_withoutToken_returns401() throws Exception {
        String payload = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":2,"
                + "\"ticketTypeId\":3,"
                + "\"orderCode\":\"SO-1\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[101]"
                + "}";

        mockMvc.perform(post("/api/seat-orders")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(seatReservationService);
    }

    @Test
    void seatOrderCreate_disallowedRole_returns403() throws Exception {
        UserAccount moderator = new UserAccount();
        moderator.setUsername("mod_1");
        moderator.setRole("MODERATOR");
        when(accountSecurityService.requireUserByToken("token-moderator")).thenReturn(moderator);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("MODERATOR", "SENIOR", "FAMILY_MEMBER", "SERVICE_STAFF", "ORG_ADMIN", "PLATFORM_ADMIN");

        String payload = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":2,"
                + "\"ticketTypeId\":3,"
                + "\"orderCode\":\"SO-2\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[101]"
                + "}";

        mockMvc.perform(post("/api/seat-orders")
                        .header("X-Auth-Token", "token-moderator")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isForbidden());

        verifyNoInteractions(seatReservationService);
    }

    @Test
    void seatOrderCreate_allowedRole_overwritesBuyerReference() throws Exception {
        UserAccount senior = new UserAccount();
        senior.setUsername("senior_a");
        senior.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-senior")).thenReturn(senior);

        SeatOrderResponse response = new SeatOrderResponse();
        response.setOrderId(88L);
        response.setOrderCode("SO-3");
        response.setStatus("PENDING");
        response.setQuantity(1);
        when(seatReservationService.createSeatOrder(any())).thenReturn(response);

        String payload = "{"
                + "\"eventId\":1,"
                + "\"sessionId\":2,"
                + "\"ticketTypeId\":3,"
                + "\"orderCode\":\"SO-3\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"seatIds\":[101]"
                + "}";

        mockMvc.perform(post("/api/seat-orders")
                        .header("X-Auth-Token", "token-senior")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateSeatOrderRequest> requestCaptor = ArgumentCaptor.forClass(CreateSeatOrderRequest.class);
        verify(seatReservationService).createSeatOrder(requestCaptor.capture());
        assertEquals("senior_a", requestCaptor.getValue().getBuyerReference());
    }

    @Test
    void seatOrderPay_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/seat-orders/10/pay"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(seatReservationService);
    }

    @Test
    void ticketReservation_withoutToken_returns401() throws Exception {
        String payload = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"RES-1\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        mockMvc.perform(post("/api/tickets/reservations")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ticketingService);
    }

    @Test
    void ticketReservation_overwritesForgedBuyerReferenceFromTokenUser() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("senior_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-senior")).thenReturn(user);

        ReservationResponse response = new ReservationResponse();
        response.setReservationId(77L);
        response.setReservationCode("RES-2");
        response.setStatus("CONFIRMED");
        when(ticketingService.reserveTickets(any(), any())).thenReturn(response);

        String payload = "{"
                + "\"ticketTypeId\":1,"
                + "\"reservationCode\":\"RES-2\","
                + "\"buyerReference\":\"forged\","
                + "\"channel\":\"ONLINE_PORTAL\","
                + "\"quantity\":1"
                + "}";

        mockMvc.perform(post("/api/tickets/reservations")
                        .header("X-Auth-Token", "token-senior")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<com.eaglepoint.venue.api.dto.CreateReservationRequest> requestCaptor = ArgumentCaptor.forClass(com.eaglepoint.venue.api.dto.CreateReservationRequest.class);
        verify(ticketingService).reserveTickets(actorCaptor.capture(), requestCaptor.capture());
        assertEquals("senior_a", actorCaptor.getValue());
        assertEquals("senior_a", requestCaptor.getValue().getBuyerReference());
    }

    @Test
    void publishingReadEndpoints_withoutToken_return401() throws Exception {
        mockMvc.perform(get("/api/publishing/content"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/versions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/diff")
                        .param("leftVersion", "1")
                        .param("rightVersion", "2"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishingReadEndpoints_nonOwnerNonPrivileged_return403() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-user-a")).thenReturn(user);
        when(publishingWorkflowService.contentOwner(100L)).thenReturn("owner_b");

        mockMvc.perform(get("/api/publishing/content/100/versions")
                        .header("X-Auth-Token", "token-user-a"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/publishing/content/100/diff")
                        .header("X-Auth-Token", "token-user-a")
                        .param("leftVersion", "1")
                        .param("rightVersion", "2"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/publishing/content/100/audit")
                        .header("X-Auth-Token", "token-user-a"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishingReadEndpoints_ownerOrPrivileged_canAccess() throws Exception {
        UserAccount owner = new UserAccount();
        owner.setUsername("owner_a");
        owner.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-owner")).thenReturn(owner);
        when(publishingWorkflowService.contentOwner(100L)).thenReturn("owner_a");
        when(publishingWorkflowService.versions(100L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/publishing/content/100/versions")
                        .header("X-Auth-Token", "token-owner"))
                .andExpect(status().isOk());

        DiffResponse diffResponse = new DiffResponse();
        diffResponse.setContentId(100L);
        diffResponse.setLeftVersion(1);
        diffResponse.setRightVersion(2);
        diffResponse.setLeftLines(Collections.singletonList("left"));
        diffResponse.setRightLines(Collections.singletonList("right"));
        when(publishingWorkflowService.diff(100L, 1, 2)).thenReturn(diffResponse);
        when(publishingWorkflowService.auditTrail(100L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/publishing/content/100/diff")
                        .header("X-Auth-Token", "token-owner")
                        .param("leftVersion", "1")
                        .param("rightVersion", "2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/publishing/content/100/audit")
                        .header("X-Auth-Token", "token-owner"))
                .andExpect(status().isOk());

        UserAccount admin = new UserAccount();
        admin.setUsername("admin_a");
        admin.setRole("ORG_ADMIN");
        when(accountSecurityService.requireUserByToken("token-admin")).thenReturn(admin);
        when(publishingWorkflowService.listAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/publishing/content")
                        .header("X-Auth-Token", "token-admin"))
                .andExpect(status().isOk());
    }

    @Test
    void seatOrderPay_disallowedRole_returns403() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("mod_1");
        user.setRole("MODERATOR");
        when(accountSecurityService.requireUserByToken("token-mod")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("MODERATOR", "SENIOR", "FAMILY_MEMBER", "SERVICE_STAFF", "ORG_ADMIN", "PLATFORM_ADMIN");

        mockMvc.perform(post("/api/seat-orders/10/pay")
                        .header("X-Auth-Token", "token-mod"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(seatReservationService);
    }

    @Test
    void seatOrderPay_allowedRole_returns200() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("senior_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-senior")).thenReturn(user);

        SeatOrderResponse response = new SeatOrderResponse();
        response.setOrderId(10L);
        response.setOrderCode("SO-10");
        response.setStatus("PAID");
        response.setQuantity(1);
        when(seatReservationService.markOrderPaid(10L, "senior_a", "SENIOR")).thenReturn(response);

        mockMvc.perform(post("/api/seat-orders/10/pay")
                        .header("X-Auth-Token", "token-senior"))
                .andExpect(status().isOk());

        verify(seatReservationService).markOrderPaid(10L, "senior_a", "SENIOR");
    }

    @Test
    void markNotificationRead_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/moderation/notifications/100/read"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(moderationService);
    }

    @Test
    void publishingCorrection_nonOwnerNonPrivileged_returns403() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("user_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-user-a")).thenReturn(user);
        when(publishingWorkflowService.contentOwner(100L)).thenReturn("owner_b");

        String payload = "{"
                + "\"title\":\"t\","
                + "\"body\":\"b\","
                + "\"summary\":\"s\""
                + "}";

        mockMvc.perform(post("/api/publishing/content/100/corrections")
                        .header("X-Auth-Token", "token-user-a")
                        .param("appealId", "300")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isForbidden());

        verify(publishingWorkflowService).contentOwner(100L);
    }

    @Test
    void publishingCorrection_ownerIsAllowed() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("owner_a");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-owner")).thenReturn(user);
        when(publishingWorkflowService.contentOwner(100L)).thenReturn("owner_a");

        ContentResponse response = new ContentResponse();
        response.setContentId(100L);
        response.setState("PUBLISH");
        when(publishingWorkflowService.applyPublishedCorrection(any(), any(), any(), any())).thenReturn(response);

        String payload = "{"
                + "\"title\":\"t\","
                + "\"body\":\"b\","
                + "\"summary\":\"s\""
                + "}";

        mockMvc.perform(post("/api/publishing/content/100/corrections")
                        .header("X-Auth-Token", "token-owner")
                        .param("appealId", "300")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk());

        verify(publishingWorkflowService).applyPublishedCorrection(any(), any(), any(), any());
    }

    @Test
    void publishingReadEndpoints_withoutToken_return401_all() throws Exception {
        mockMvc.perform(get("/api/publishing/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/versions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/diff").param("leftVersion", "1").param("rightVersion", "2")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publishing/content/100/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void ticketReservation_overwritesForgedBuyerReference() throws Exception {
        UserAccount user = new UserAccount();
        user.setUsername("legit_user");
        user.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-legit")).thenReturn(user);
        
        ReservationResponse mockResponse = new ReservationResponse();
        mockResponse.setReservationId(999L);
        mockResponse.setReservationCode("RES-TEST");
        mockResponse.setStatus("CONFIRMED");
        when(ticketingService.reserveTickets(any(), any())).thenReturn(mockResponse);

        String payload = "{\"ticketTypeId\":1,\"reservationCode\":\"R1\",\"buyerReference\":\"attacker\",\"channel\":\"ONLINE_PORTAL\",\"quantity\":1}";
        
        mockMvc.perform(post("/api/tickets/reservations")
                        .header("X-Auth-Token", "token-legit")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated());
        
        verify(ticketingService).reserveTickets(org.mockito.ArgumentMatchers.eq("legit_user"), any());
    }

    @Test
    void paymentWriteEndpoints_withoutToken_return401() throws Exception {
        String tenderBody = "{\"transactionRef\":\"T1\",\"tenderType\":\"CASH\",\"amount\":10.00,\"merchantCode\":\"M1\"}";
        mockMvc.perform(post("/api/payments/tenders").contentType(MediaType.APPLICATION_JSON).content(tenderBody)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/callbacks")
                        .param("transactionRef", "T1")
                        .param("gatewayBatchRef", "B1")
                        .param("settledAmount", "10.00")
                        .param("status", "SUCCESS"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/payments/settlements/import").file(new MockMultipartFile("file", "test.csv", "text/csv", "test".getBytes())))
                .andExpect(status().isUnauthorized());
        String refundBody = "{\"transactionRef\":\"T1\",\"amount\":5.00,\"reason\":\"chargeback\"}";
        mockMvc.perform(post("/api/payments/refunds").contentType(MediaType.APPLICATION_JSON).content(refundBody)).andExpect(status().isUnauthorized());
    }

    @Test
    void paymentWriteEndpoints_insufficientRole_return403() throws Exception {
        UserAccount senior = new UserAccount();
        senior.setUsername("senior_1");
        senior.setRole("SENIOR");
        when(accountSecurityService.requireUserByToken("token-senior")).thenReturn(senior);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "insufficient role permissions"))
                .when(accountSecurityService)
                .requireAnyRole("SENIOR", "SERVICE_STAFF", "ORG_ADMIN", "PLATFORM_ADMIN");

        // Record tender requires SERVICE_STAFF+
        String tenderBody = "{\"transactionRef\":\"T1\",\"tenderType\":\"CASH\",\"amount\":10.00,\"merchantCode\":\"M1\"}";
        mockMvc.perform(post("/api/payments/tenders")
                        .header("X-Auth-Token", "token-senior")
                        .contentType("application/json")
                        .content(tenderBody))
                .andExpect(status().isForbidden());
    }
}
