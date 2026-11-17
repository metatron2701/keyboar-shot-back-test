package com.green.keyboard_project;

import com.green.keyboard_project.config.tosspayments.TossPaymentsClient;
import com.green.keyboard_project.dto.payment.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Emits actual controller envelopes for the offline frontend contract test. No sockets/provider/real DB. */
@SpringBootTest(properties={"spring.profiles.active=local","TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true",
        "toss.payments.test.enabled=true","toss.payments.dev-confirm.enabled=true","TOSS_PAYMENTS_SECRET_KEY=test_gsk_fixture_only"})
class PaymentFlowContractIntegrationTests {
    @Autowired WebApplicationContext context;
    @MockitoBean TossPaymentsClient client;

    @Test void exportCreateLookupApprovalAndReplayContracts() throws Exception {
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        JsonMapper json=JsonMapper.builder().build();
        List<Object> cases=new ArrayList<>();
        for(String scenario:List.of("DONE","UNKNOWN_RECOVERY","REJECTED","WAITING_FOR_DEPOSIT","CONTRADICTORY_WAITING","TIMEOUT_RECOVERY")) {
            reset(client);
            var created=mvc.perform(post("/api/dev/toss-orders")).andReturn().getResponse();
            assertThat(created.getStatus()).isEqualTo(201);
            var order=json.readTree(created.getContentAsString(StandardCharsets.UTF_8)).path("data");
            String id=order.path("orderId").asText(), key="fixture_stage10_"+id;
            var lookup=mvc.perform(get("/api/dev/toss-orders/"+id)).andReturn().getResponse();
            assertThat(lookup.getStatus()).isEqualTo(200);
            assertThat(json.readTree(lookup.getContentAsString(StandardCharsets.UTF_8)).path("data")).isEqualTo(order);
            var done=new TossPaymentResponse(key,id,10000,"KRW","DONE","카드",OffsetDateTime.parse("2026-11-16T12:00:00+09:00"));
            var reply=switch(scenario) {
                case "REJECTED" -> TossPaymentsClient.Reply.failure(400,"REJECT_CARD_COMPANY",false);
                case "UNKNOWN_RECOVERY" -> TossPaymentsClient.Reply.failure(503,"PROVIDER_ERROR",true);
                case "TIMEOUT_RECOVERY" -> TossPaymentsClient.Reply.failure(0,"TRANSPORT_ERROR",true);
                case "WAITING_FOR_DEPOSIT","CONTRADICTORY_WAITING" -> TossPaymentsClient.Reply.payment(
                        new TossPaymentResponse(key,id,10000,"KRW","WAITING_FOR_DEPOSIT","가상계좌",
                                scenario.equals("CONTRADICTORY_WAITING")?done.approvedAt():null));
                default -> TossPaymentsClient.Reply.payment(done);
            };
            when(client.confirm(any(),anyLong(),anyString())).thenReturn(reply);
            when(client.find(key)).thenReturn(TossPaymentsClient.Reply.payment(done));
            var request=Map.of("paymentKey",key,"orderId",id,"amount",10000);
            List<Object> responses=new ArrayList<>();
            for(int i=0;i<2;i++) {
                var response=mvc.perform(post("/api/payments/confirm").header("Origin","http://localhost:3000")
                        .contentType("application/json").content(json.writeValueAsString(request))).andReturn().getResponse();
                var body=json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
                assertThat(response.getStatus()).isEqualTo(scenario.equals("REJECTED")?422:
                        (i==0 && Set.of("UNKNOWN_RECOVERY","TIMEOUT_RECOVERY","CONTRADICTORY_WAITING").contains(scenario)?202:200));
                assertThat(response.getContentAsString()).doesNotContain(key);
                responses.add(Map.of("status",response.getStatus(),"body",body));
            }
            verify(client,times(1)).confirm(any(),eq(10000L),anyString());
            verify(client,times(Set.of("UNKNOWN_RECOVERY","TIMEOUT_RECOVERY","CONTRADICTORY_WAITING").contains(scenario)?1:0)).find(anyString());
            cases.add(Map.of("scenario",scenario,"created",json.readTree(created.getContentAsString(StandardCharsets.UTF_8)),
                    "lookup",json.readTree(lookup.getContentAsString(StandardCharsets.UTF_8)),"request",request,"responses",responses));
        }
        Path output=Path.of("build/stage10/contract.json"); Files.createDirectories(output.getParent());
        Files.writeString(output,json.writeValueAsString(Map.of("version",1,"cases",cases)));
    }
}
