package com.green.keyboard_project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.green.keyboard_project.entity.CartItem;
import com.green.keyboard_project.entity.Category;
import com.green.keyboard_project.entity.ManualBuild;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductSpec;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.repository.AutoBuildRepository;
import com.green.keyboard_project.repository.CategoryRepository;
import com.green.keyboard_project.repository.ManualBuildRepository;
import com.green.keyboard_project.repository.ProductRepository;
import com.green.keyboard_project.repository.ProductSpecRepository;
import com.green.keyboard_project.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class BuildControllerIntegrationTests {

    @Autowired WebApplicationContext context;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductSpecRepository productSpecRepository;
    @Autowired ManualBuildRepository manualBuildRepository;
    @Autowired AutoBuildRepository autoBuildRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mvc;

    private Product housing87;
    private Product housing65;
    private Product pcb87Wireless;
    private Product pcb65FivePinOnly;
    private Product switchLinear5Pin;
    private Product switchTactile3Pin;
    private Product keycapPbt;
    private Product customKeycap;
    private Product soldOutKeycap;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();

        Category catHousing = saveCategory("housing", null, "하우징", 1);
        Category catPcb = saveCategory("pcb", null, "기판 (PCB)", 2);
        Category catSwitch = saveCategory("switches", null, "스위치", 3);
        Category catKeycap = saveCategory("keycaps", null, "키캡", 4);

        Category catHousingAlu = saveCategory("housing-alu", catHousing, "알루미늄", 1);
        Category catPcbWireless = saveCategory("pcb-wireless", catPcb, "유무선", 2);
        Category catPcbHotswap = saveCategory("pcb-hotswap", catPcb, "핫스왑", 3);
        Category catSwitchLinear = saveCategory("switch-linear", catSwitch, "리니어", 4);
        Category catSwitchTactile = saveCategory("switch-tactile", catSwitch, "택타일", 5);
        Category catKeycapPbt = saveCategory("keycap-pbt", catKeycap, "PBT", 6);
        Category catKeycapCustom = saveCategory("keycap-custom", catKeycap, "커스텀 풀 컬러 키캡", 7);

        customKeycap = saveProduct(catKeycapCustom, "커스텀 풀 컬러 키캡 (R1)", 2700, 999, true);
        saveSpec(customKeycap, null, null, null, null, null, null, "PBT");

        housing87 = saveProduct(catHousingAlu, "알루미늄 87 하우징", 100000, 10, true);
        saveSpec(housing87, "87", "알루미늄", null, null, null, null, null);

        housing65 = saveProduct(catHousingAlu, "알루미늄 65 하우징", 90000, 10, true);
        saveSpec(housing65, "65", "알루미늄", null, null, null, null, null);

        pcb87Wireless = saveProduct(catPcbWireless, "87 무선 기판", 50000, 10, true);
        saveSpec(pcb87Wireless, "87", null, "3핀/5핀", true, null, null, null);

        pcb65FivePinOnly = saveProduct(catPcbHotswap, "65 5핀 전용 기판", 45000, 10, true);
        saveSpec(pcb65FivePinOnly, "65", null, "5핀", false, null, null, null);

        switchLinear5Pin = saveProduct(catSwitchLinear, "리니어 5핀 스위치 90개입", 30000, 10, true);
        saveSpec(switchLinear5Pin, null, null, null, null, 5, "리니어", null);

        switchTactile3Pin = saveProduct(catSwitchTactile, "택타일 3핀 스위치 90개입", 28000, 10, true);
        saveSpec(switchTactile3Pin, null, null, null, null, 3, "택타일", null);

        keycapPbt = saveProduct(catKeycapPbt, "PBT 체리 키캡", 40000, 10, true);
        saveSpec(keycapPbt, null, null, null, null, null, null, "PBT");

        soldOutKeycap = saveProduct(catKeycapPbt, "품절 키캡", 35000, 0, true);
        saveSpec(soldOutKeycap, null, null, null, null, null, null, "PBT");
    }

    @Test
    void createManualBuildCalculatesPriceOnServer() throws Exception {
        Cookie[] cookies = signupAndLogin("builder1@example.com");

        String body = """
                {
                  "buildName": "내 첫 87 커스텀",
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d,
                  "includeAssembly": true
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                keycapPbt.getProductId()
        );

        String responseJson = mvc.perform(post("/api/builds/manual")
                        .cookie(cookies)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buildType").value("MANUAL"))
                .andExpect(jsonPath("$.data.assemblyFee").value(20000))
                .andExpect(jsonPath("$.data.totalPrice").value(240000))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(responseJson).get("data");
        int buildId = data.get("buildId").asInt();

        mvc.perform(get("/api/builds/manual/" + buildId).cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildName").value("내 첫 87 커스텀"));
    }

    @Test
    void rejectIncompatibleLayoutAndUnsupportedSwitchPinAndSoldOutPart() throws Exception {
        // 1) 87 하우징 + 65 기판 -> 배열 불일치 400
        String layoutMismatch = """
                {
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d
                }
                """.formatted(
                housing87.getProductId(),
                pcb65FivePinOnly.getProductId(),
                switchLinear5Pin.getProductId(),
                keycapPbt.getProductId()
        );
        mvc.perform(post("/api/builds/manual").contentType("application/json").content(layoutMismatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 2) 65 5핀 전용 기판 + 3핀 스위치 -> 핀 호환 불가 400
        String pinMismatch = """
                {
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d
                }
                """.formatted(
                housing65.getProductId(),
                pcb65FivePinOnly.getProductId(),
                switchTactile3Pin.getProductId(),
                keycapPbt.getProductId()
        );
        mvc.perform(post("/api/builds/manual").contentType("application/json").content(pinMismatch))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 3) 품절 키캡 선택 -> 400
        String soldOutRequest = """
                {
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                soldOutKeycap.getProductId()
        );
        mvc.perform(post("/api/builds/manual").contentType("application/json").content(soldOutRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 4) keycap-custom 단품 키캡 선택 -> 400
        String customKeycapRequest = """
                {
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                customKeycap.getProductId()
        );
        mvc.perform(post("/api/builds/manual").contentType("application/json").content(customKeycapRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateManualBuildUsesCopyOnWriteWhenReferencedByCart() throws Exception {
        Cookie[] cookies = signupAndLogin("cow@example.com");
        User user = userRepository.findByEmail("cow@example.com").orElseThrow();

        String createBody = """
                {
                  "buildName": "원본 빌드",
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d,
                  "includeAssembly": true
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                keycapPbt.getProductId()
        );

        String createdJson = mvc.perform(post("/api/builds/manual")
                        .cookie(cookies)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int originalBuildId = objectMapper.readTree(createdJson).get("data").get("buildId").asInt();

        // 1) 미참조 상태 수정 -> 동일 ID 유지 (copiedOnWrite = false)
        String inPlaceBody = """
                {
                  "buildName": "직접 수정된 빌드",
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d,
                  "includeAssembly": false
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                keycapPbt.getProductId()
        );
        mvc.perform(put("/api/builds/manual/" + originalBuildId)
                        .cookie(cookies)
                        .contentType("application/json")
                        .content(inPlaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildId").value(originalBuildId))
                .andExpect(jsonPath("$.data.copiedOnWrite").value(false))
                .andExpect(jsonPath("$.data.totalPrice").value(220000));

        // 2) 장바구니에 담긴 뒤 수정 -> Copy-on-Write (새 buildId 발급, copiedOnWrite = true)
        ManualBuild original = manualBuildRepository.findById(originalBuildId).orElseThrow();
        CartItem cartItem = new CartItem();
        cartItem.setUser(user);
        cartItem.setManualBuild(original);
        cartItem.setQuantity(1);
        cartItem.setCreatedAt(LocalDateTime.now());
        cartItem.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.saveAndFlush(cartItem);

        String cowBody = """
                {
                  "buildName": "복제된 새 빌드",
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d,
                  "includeAssembly": true
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchTactile3Pin.getProductId(),
                keycapPbt.getProductId()
        );
        String cowJson = mvc.perform(put("/api/builds/manual/" + originalBuildId)
                        .cookie(cookies)
                        .contentType("application/json")
                        .content(cowBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.copiedOnWrite").value(true))
                .andExpect(jsonPath("$.data.buildName").value("복제된 새 빌드"))
                .andExpect(jsonPath("$.data.totalPrice").value(238000))
                .andReturn().getResponse().getContentAsString();

        int clonedBuildId = objectMapper.readTree(cowJson).get("data").get("buildId").asInt();
        assertThat(clonedBuildId).isNotEqualTo(originalBuildId);
        assertThat(manualBuildRepository.findById(originalBuildId).orElseThrow().getBuildName()).isEqualTo("직접 수정된 빌드");
    }

    @Test
    void recommendAndSaveAutoBuildAndEnforceOwnership() throws Exception {
        Cookie[] ownerCookies = signupAndLogin("owner@example.com");
        Cookie[] otherCookies = signupAndLogin("other@example.com");

        String recommendBody = """
                {
                  "surveyLayout": "87",
                  "surveyConnectivity": "wireless",
                  "surveySound": "thock",
                  "surveyKeycap": "PBT",
                  "includeAssembly": true
                }
                """;
        mvc.perform(post("/api/builds/auto/recommend")
                        .contentType("application/json")
                        .content(recommendBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buildType").value("AUTO"))
                .andExpect(jsonPath("$.data.totalPrice").value(240000));

        String saveAutoBody = """
                {
                  "buildName": "설문 추천 87",
                  "surveyLayout": "87",
                  "surveyConnectivity": "wireless",
                  "surveySound": "thock",
                  "surveyKeycap": "PBT",
                  "housingProductId": %d,
                  "pcbProductId": %d,
                  "switchProductId": %d,
                  "keycapProductId": %d,
                  "includeAssembly": true
                }
                """.formatted(
                housing87.getProductId(),
                pcb87Wireless.getProductId(),
                switchLinear5Pin.getProductId(),
                keycapPbt.getProductId()
        );
        String savedJson = mvc.perform(post("/api/builds/auto")
                        .cookie(ownerCookies)
                        .contentType("application/json")
                        .content(saveAutoBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalPrice").value(240000))
                .andReturn().getResponse().getContentAsString();

        int autoBuildId = objectMapper.readTree(savedJson).get("data").get("buildId").asInt();
        assertThat(autoBuildRepository.findById(autoBuildId)).isPresent();

        // 비로그인 / 타인 조회 차단 확인
        mvc.perform(get("/api/builds/auto/" + autoBuildId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/builds/auto/" + autoBuildId).cookie(otherCookies))
                .andExpect(status().isForbidden());
    }

    private Category saveCategory(String id, Category parent, String name, int order) {
        Category c = new Category(id, parent, name, order);
        return categoryRepository.save(c);
    }

    private Product saveProduct(Category category, String name, int price, int stock, boolean active) {
        Product p = new Product();
        p.setCategory(category);
        p.setName(name);
        p.setBasePrice(price);
        p.setStockQuantity(stock);
        p.setThumbnailUrl("https://example.com/" + name + ".jpg");
        p.setActive(active);
        p.setDeleted(false);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(p);
    }

    private void saveSpec(
            Product p,
            String layout,
            String housingMaterial,
            String supportPins,
            Boolean isWireless,
            Integer switchPin,
            String switchType,
            String keycapMaterial
    ) {
        ProductSpec spec = new ProductSpec();
        spec.setProduct(p);
        spec.setLayout(layout);
        spec.setHousingMaterial(housingMaterial);
        spec.setSupportPins(supportPins);
        spec.setIsWireless(isWireless);
        spec.setSwitchPin(switchPin);
        spec.setSwitchType(switchType);
        spec.setKeycapMaterial(keycapMaterial);
        productSpecRepository.save(spec);
    }

    private Cookie[] signupAndLogin(String email) throws Exception {
        mvc.perform(post("/api/users/signup")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Keyboard123",
                                  "name": "테스트회원",
                                  "phoneNumber": "01012345678",
                                  "defaultAddress": {"zonecode": "04524", "roadAddress": "서울 세종대로", "detailAddress": "101호"},
                                  "termsConsent": {"serviceTerms": true, "privacyPolicy": true}
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        var res = mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Keyboard123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        return res.getHeaders("Set-Cookie").stream()
                .map(v -> {
                    String[] pair = v.split(";", 2)[0].split("=", 2);
                    return new Cookie(pair[0], pair[1]);
                })
                .toArray(Cookie[]::new);
    }
}
