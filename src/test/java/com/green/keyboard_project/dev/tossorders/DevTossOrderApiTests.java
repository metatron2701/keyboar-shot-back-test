package com.green.keyboard_project.dev.tossorders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DevTossOrderApiTests {
    @Configuration @EnableWebSecurity static class Security {}
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class))
        .withUserConfiguration(Security.class, DevTossOrdersConfiguration.class)
        .withPropertyValues("TOSS_PAYMENTS_DEV_ORDERS_ENABLED=true", "spring.profiles.active=local");

    @Test void apiValidatesInputsAndPreservesOtherSecurity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
            var response = mvc.perform(post("/api/dev/toss-orders")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.amount").value(10000))
                .andReturn().getResponse().getContentAsString();
            String id = java.util.regex.Pattern.compile("dev_[a-f0-9]{32}").matcher(response).results().findFirst().orElseThrow().group();
            mvc.perform(get("/api/dev/toss-orders/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(id));
            mvc.perform(post("/api/dev/toss-orders").contentType("application/json").content("{\"amount\":1}"))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/dev/toss-orders?amount=1")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/dev/toss-orders/bad")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/dev/toss-orders/dev_"+"a".repeat(32))).andExpect(status().isNotFound());
            mvc.perform(post("/api/dev/toss-orders").with(request -> {request.setRemoteAddr("192.0.2.1");return request;}))
                .andExpect(status().isForbidden());
            mvc.perform(options("/api/dev/toss-orders").header("Origin","http://localhost:3000")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
            mvc.perform(options("/api/dev/toss-orders").header("Origin","https://example.com")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
            mvc.perform(delete("/api/private-example")).andExpect(status().isForbidden());
            mvc.perform(get("/api/private-example")).andExpect(status().isUnauthorized());
        });
    }
    @Test void noOptInNoLocalAndProductionNeverRegisterTestApi() {
        for (String profile : new String[]{"default", "prod", "production", "local,prod", "local,production"}) {
            runner.withPropertyValues("spring.profiles.active="+profile).run(context -> {
                assertThat(context).doesNotHaveBean(DevTossOrderController.class);
                assertThat(context).doesNotHaveBean(DevTossOrderService.class);
                assertThat(context).doesNotHaveBean("devTossOrderSecurity");
                var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(FilterChainProxy.class)).build();
                mvc.perform(get("/api/dev/toss-orders/dev_"+"a".repeat(32))).andExpect(status().isUnauthorized());
                mvc.perform(post("/api/dev/toss-orders" )).andExpect(status().isForbidden());
            });
        }
        runner.withPropertyValues("TOSS_PAYMENTS_DEV_ORDERS_ENABLED=false").run(context -> {
            assertThat(context).doesNotHaveBean(DevTossOrderController.class);
            assertThat(context).doesNotHaveBean("devTossOrderSecurity");
        });
    }
}
