package com.green.keyboard_project.service;

import com.green.keyboard_project.dto.build.AutoBuildRecommendRequest;
import com.green.keyboard_project.dto.build.AutoBuildRequest;
import com.green.keyboard_project.dto.build.BuildResponse;
import com.green.keyboard_project.dto.build.ManualBuildRequest;
import com.green.keyboard_project.entity.AutoBuild;
import com.green.keyboard_project.entity.AutoBuildItem;
import com.green.keyboard_project.entity.ManualBuild;
import com.green.keyboard_project.entity.ManualBuildItem;
import com.green.keyboard_project.entity.Product;
import com.green.keyboard_project.entity.ProductSpec;
import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.AuthorizationFailureException;
import com.green.keyboard_project.exceptions.InvalidStateException;
import com.green.keyboard_project.exceptions.ResourceNotFoundException;
import com.green.keyboard_project.exceptions.UnauthenticatedException;
import com.green.keyboard_project.repository.AutoBuildItemRepository;
import com.green.keyboard_project.repository.AutoBuildRepository;
import com.green.keyboard_project.repository.ManualBuildItemRepository;
import com.green.keyboard_project.repository.ManualBuildRepository;
import com.green.keyboard_project.repository.ProductRepository;
import com.green.keyboard_project.repository.ProductSpecRepository;
import com.green.keyboard_project.repository.UserRepository;
import com.green.keyboard_project.repository.cart.CartItemRepository;
import com.green.keyboard_project.repository.orders.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildService {

    public static final int ASSEMBLY_FEE = 20_000;
    private static final String DEFAULT_MANUAL_BUILD_NAME = "나만의 수동 커스텀 키보드";
    private static final String DEFAULT_AUTO_BUILD_NAME = "자동 맞춤 커스텀 키보드";
    private static final List<Integer> DEFAULT_SUPPORT_PINS = List.of(3, 5);

    private final ProductRepository productRepository;
    private final ProductSpecRepository productSpecRepository;
    private final UserRepository userRepository;
    private final ManualBuildRepository manualBuildRepository;
    private final ManualBuildItemRepository manualBuildItemRepository;
    private final AutoBuildRepository autoBuildRepository;
    private final AutoBuildItemRepository autoBuildItemRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;

    // ==================== 수동 빌드 ====================

    @Transactional
    public BuildResponse createManualBuild(Integer userId, ManualBuildRequest request) {
        boolean includeAssembly = !Boolean.FALSE.equals(request.getIncludeAssembly());
        ValidatedParts validated = validateAndResolveParts(
                request.getHousingProductId(),
                request.getPcbProductId(),
                request.getSwitchProductId(),
                request.getKeycapProductId(),
                includeAssembly
        );
        User owner = resolveActorUser(userId);
        return persistNewManualBuild(owner, normalizeName(request.getBuildName(), DEFAULT_MANUAL_BUILD_NAME), validated, false);
    }

    public BuildResponse getManualBuild(Integer userId, int buildId) {
        ManualBuild build = manualBuildRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("수동 빌드를 찾을 수 없습니다. (ID: " + buildId + ")"));
        verifyAccess(build.getUser(), userId, "본인의 빌드만 조회할 수 있습니다.");

        List<ManualBuildItem> items = manualBuildItemRepository.findByManualBuild_ManualBuildIdOrderByManualItemIdAsc(buildId);
        Map<Integer, ProductSpec> specMap = loadSpecMap(items.stream().map(i -> i.getProduct().getProductId()).toList());
        List<BuildResponse.BuildItemDto> itemDtos = items.stream()
                .map(item -> toBuildItemDto(item.getProduct(), specMap.get(item.getProduct().getProductId()), item.getUnitPrice()))
                .toList();

        return toManualBuildResponse(build, itemDtos, false);
    }

    /**
     * 수동 빌드 수정 (장바구니·주문에 이미 참조 중이면 Copy-on-Write로 새 빌드 복제 생성)
     */
    @Transactional
    public BuildResponse updateManualBuild(Integer userId, int buildId, ManualBuildRequest request) {
        ManualBuild existing = manualBuildRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("수동 빌드를 찾을 수 없습니다. (ID: " + buildId + ")"));
        verifyAccess(existing.getUser(), userId, "본인의 빌드만 수정할 수 있습니다.");

        boolean includeAssembly = !Boolean.FALSE.equals(request.getIncludeAssembly());
        ValidatedParts validated = validateAndResolveParts(
                request.getHousingProductId(),
                request.getPcbProductId(),
                request.getSwitchProductId(),
                request.getKeycapProductId(),
                includeAssembly
        );
        String resolvedName = normalizeName(request.getBuildName(), existing.getBuildName());

        boolean inUse = cartItemRepository.existsByManualBuild_ManualBuildId(buildId)
                || orderItemRepository.existsByManualBuild_ManualBuildId(buildId);
        if (inUse) {
            User owner = existing.getUser() != null ? existing.getUser() : resolveActorUser(userId);
            return persistNewManualBuild(owner, resolvedName, validated, true);
        }

        existing.setBuildName(resolvedName);
        existing.setIncludeAssembly(validated.includeAssembly());
        existing.setAssemblyFee(validated.assemblyFee());
        existing.setTotalPrice(validated.totalPrice());
        manualBuildRepository.save(existing);

        manualBuildItemRepository.deleteByManualBuild_ManualBuildId(buildId);
        manualBuildItemRepository.flush();
        saveManualBuildItems(existing, validated);

        return toManualBuildResponse(existing, validated.toItemDtos(), false);
    }

    // ==================== 자동 빌드 (설문 추천 & 저장 & 수정) ====================

    public BuildResponse recommendAutoBuild(AutoBuildRecommendRequest request) {
        List<Product> available = productRepository
                .findByIsDeletedFalseAndIsActiveTrueAndStockQuantityGreaterThanOrderByProductIdAsc(0);
        Map<Integer, ProductSpec> specMap = loadSpecMap(available.stream().map(Product::getProductId).toList());

        List<Product> housings = filterByPartType(available, "HOUSING");
        List<Product> pcbs = filterByPartType(available, "PCB");
        List<Product> switches = filterByPartType(available, "SWITCH");
        List<Product> keycaps = filterByPartType(available, "KEYCAP");

        Product housing = selectHousing(housings, specMap, request.getSurveyLayout());
        String targetLayout = specLayout(specMap.get(housing.getProductId()));
        Product pcb = selectPcb(pcbs, specMap, targetLayout, request.getSurveyConnectivity());
        List<Integer> pcbPins = resolveSupportPins(specMap.get(pcb.getProductId()));
        Product sw = selectSwitch(switches, specMap, pcbPins, request.getSurveySound());
        Product keycap = selectKeycap(keycaps, specMap, request.getSurveyKeycap());

        boolean includeAssembly = !Boolean.FALSE.equals(request.getIncludeAssembly());
        ValidatedParts validated = validateAndResolveParts(
                housing.getProductId(),
                pcb.getProductId(),
                sw.getProductId(),
                keycap.getProductId(),
                includeAssembly
        );

        return BuildResponse.builder()
                .buildId(null)
                .buildType("AUTO")
                .userId(null)
                .buildName(DEFAULT_AUTO_BUILD_NAME)
                .includeAssembly(validated.includeAssembly())
                .assemblyFee(validated.assemblyFee())
                .totalPrice(validated.totalPrice())
                .copiedOnWrite(false)
                .createdAt(null)
                .surveyLayout(request.getSurveyLayout().trim())
                .surveyConnectivity(normalizeName(request.getSurveyConnectivity(), "any"))
                .surveySound(normalizeName(request.getSurveySound(), "thock"))
                .surveyKeycap(normalizeName(request.getSurveyKeycap(), "PBT"))
                .items(validated.toItemDtos())
                .build();
    }

    @Transactional
    public BuildResponse createAutoBuild(Integer userId, AutoBuildRequest request) {
        boolean includeAssembly = !Boolean.FALSE.equals(request.getIncludeAssembly());
        ValidatedParts validated = validateAndResolveParts(
                request.getHousingProductId(),
                request.getPcbProductId(),
                request.getSwitchProductId(),
                request.getKeycapProductId(),
                includeAssembly
        );
        User owner = resolveActorUser(userId);
        return persistNewAutoBuild(owner, normalizeName(request.getBuildName(), DEFAULT_AUTO_BUILD_NAME), request, validated, false);
    }

    public BuildResponse getAutoBuild(Integer userId, int buildId) {
        AutoBuild build = autoBuildRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("자동 빌드를 찾을 수 없습니다. (ID: " + buildId + ")"));
        verifyAccess(build.getUser(), userId, "본인의 빌드만 조회할 수 있습니다.");

        List<AutoBuildItem> items = autoBuildItemRepository.findByAutoBuild_AutoBuildIdOrderByAutoItemIdAsc(buildId);
        Map<Integer, ProductSpec> specMap = loadSpecMap(items.stream().map(i -> i.getProduct().getProductId()).toList());
        List<BuildResponse.BuildItemDto> itemDtos = items.stream()
                .map(item -> toBuildItemDto(item.getProduct(), specMap.get(item.getProduct().getProductId()), item.getUnitPrice()))
                .toList();

        return toAutoBuildResponse(build, itemDtos, false);
    }

    /**
     * 자동 빌드 수정 (장바구니·주문에 이미 참조 중이면 Copy-on-Write로 새 빌드 복제 생성)
     */
    @Transactional
    public BuildResponse updateAutoBuild(Integer userId, int buildId, AutoBuildRequest request) {
        AutoBuild existing = autoBuildRepository.findById(buildId)
                .orElseThrow(() -> new ResourceNotFoundException("자동 빌드를 찾을 수 없습니다. (ID: " + buildId + ")"));
        verifyAccess(existing.getUser(), userId, "본인의 빌드만 수정할 수 있습니다.");

        boolean includeAssembly = !Boolean.FALSE.equals(request.getIncludeAssembly());
        ValidatedParts validated = validateAndResolveParts(
                request.getHousingProductId(),
                request.getPcbProductId(),
                request.getSwitchProductId(),
                request.getKeycapProductId(),
                includeAssembly
        );
        String resolvedName = normalizeName(request.getBuildName(), existing.getBuildName());

        boolean inUse = cartItemRepository.existsByAutoBuild_AutoBuildId(buildId)
                || orderItemRepository.existsByAutoBuild_AutoBuildId(buildId);
        if (inUse) {
            User owner = existing.getUser() != null ? existing.getUser() : resolveActorUser(userId);
            return persistNewAutoBuild(owner, resolvedName, request, validated, true);
        }

        existing.setBuildName(resolvedName);
        existing.setSurveyLayout(request.getSurveyLayout().trim());
        existing.setSurveyConnectivity(request.getSurveyConnectivity().trim());
        existing.setSurveySound(request.getSurveySound().trim());
        existing.setSurveyKeycap(request.getSurveyKeycap().trim());
        existing.setIncludeAssembly(validated.includeAssembly());
        existing.setAssemblyFee(validated.assemblyFee());
        existing.setTotalPrice(validated.totalPrice());
        autoBuildRepository.save(existing);

        autoBuildItemRepository.deleteByAutoBuild_AutoBuildId(buildId);
        autoBuildItemRepository.flush();
        saveAutoBuildItems(existing, validated);

        return toAutoBuildResponse(existing, validated.toItemDtos(), false);
    }

    // ==================== 검증 및 가격 재계산 공통 헬퍼 ====================

    private ValidatedParts validateAndResolveParts(
            int housingId,
            int pcbId,
            int switchId,
            int keycapId,
            boolean includeAssembly
    ) {
        Set<Integer> distinctIds = new HashSet<>(List.of(housingId, pcbId, switchId, keycapId));
        if (distinctIds.size() != 4) {
            throw new InvalidStateException("하우징, 기판, 스위치, 키캡은 각각 서로 다른 상품으로 선택해야 합니다.");
        }

        Map<Integer, ProductSpec> specMap = loadSpecMap(List.of(housingId, pcbId, switchId, keycapId));
        PartWithSpec housing = loadValidatedPart(housingId, "HOUSING", "하우징", specMap);
        PartWithSpec pcb = loadValidatedPart(pcbId, "PCB", "기판", specMap);
        PartWithSpec sw = loadValidatedPart(switchId, "SWITCH", "스위치", specMap);
        PartWithSpec keycap = loadValidatedPart(keycapId, "KEYCAP", "키캡", specMap);

        validateCompatibility(housing, pcb, sw);

        int assemblyFee = includeAssembly ? ASSEMBLY_FEE : 0;
        int totalPrice = housing.product().getBasePrice()
                + pcb.product().getBasePrice()
                + sw.product().getBasePrice()
                + keycap.product().getBasePrice()
                + assemblyFee;

        return new ValidatedParts(housing, pcb, sw, keycap, includeAssembly, assemblyFee, totalPrice);
    }

    private PartWithSpec loadValidatedPart(
            int productId,
            String expectedPartType,
            String partLabel,
            Map<Integer, ProductSpec> specMap
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(partLabel + " 상품을 찾을 수 없습니다. (ID: " + productId + ")"));

        if (product.isDeleted() || !product.isActive() || product.getStockQuantity() <= 0) {
            throw new InvalidStateException("현재 구매할 수 없는 " + partLabel + " 상품입니다: " + product.getName());
        }

        String actualPartType = product.getDerivedPartType();
        if (!expectedPartType.equals(actualPartType)) {
            throw new InvalidStateException(
                    partLabel + " 위치에는 해당 카테고리 상품만 선택할 수 있습니다. (상품 ID: " + productId + ")"
            );
        }

        if ("KEYCAP".equals(expectedPartType) && isCustomKeycap(product)) {
            throw new InvalidStateException("커스텀 단품 키캡은 풀 키보드 빌드의 키캡 세트로 선택할 수 없습니다.");
        }

        return new PartWithSpec(product, specMap.get(productId));
    }

    private void validateCompatibility(PartWithSpec housing, PartWithSpec pcb, PartWithSpec sw) {
        String housingLayout = specLayout(housing.spec());
        String pcbLayout = specLayout(pcb.spec());
        if (housingLayout != null && pcbLayout != null && !housingLayout.equalsIgnoreCase(pcbLayout)) {
            throw new InvalidStateException(
                    "선택한 하우징(" + housingLayout + ")과 기판(" + pcbLayout + ")의 배열 규격이 호환되지 않습니다."
            );
        }

        if (sw.spec() != null && sw.spec().getSwitchPin() != null) {
            List<Integer> supportedPins = resolveSupportPins(pcb.spec());
            int switchPin = sw.spec().getSwitchPin();
            if (!supportedPins.contains(switchPin)) {
                throw new InvalidStateException(
                        "선택한 기판은 " + switchPin + "핀 스위치를 지원하지 않습니다."
                );
            }
        }
    }

    // ==================== 저장/권한/추천 보조 헬퍼 ====================

    private BuildResponse persistNewManualBuild(
            User owner,
            String buildName,
            ValidatedParts validated,
            boolean copiedOnWrite
    ) {
        ManualBuild build = new ManualBuild();
        build.setUser(owner);
        build.setBuildName(buildName);
        build.setIncludeAssembly(validated.includeAssembly());
        build.setAssemblyFee(validated.assemblyFee());
        build.setTotalPrice(validated.totalPrice());
        build.setCreatedAt(LocalDateTime.now());
        ManualBuild saved = manualBuildRepository.save(build);

        saveManualBuildItems(saved, validated);
        return toManualBuildResponse(saved, validated.toItemDtos(), copiedOnWrite);
    }

    private void saveManualBuildItems(ManualBuild build, ValidatedParts validated) {
        List<ManualBuildItem> items = validated.parts().stream().map(part -> {
            ManualBuildItem item = new ManualBuildItem();
            item.setManualBuild(build);
            item.setProduct(part.product());
            item.setUnitPrice(part.product().getBasePrice());
            return item;
        }).toList();
        manualBuildItemRepository.saveAll(items);
    }

    private BuildResponse persistNewAutoBuild(
            User owner,
            String buildName,
            AutoBuildRequest request,
            ValidatedParts validated,
            boolean copiedOnWrite
    ) {
        AutoBuild build = new AutoBuild();
        build.setUser(owner);
        build.setBuildName(buildName);
        build.setSurveyLayout(request.getSurveyLayout().trim());
        build.setSurveyConnectivity(request.getSurveyConnectivity().trim());
        build.setSurveySound(request.getSurveySound().trim());
        build.setSurveyKeycap(request.getSurveyKeycap().trim());
        build.setIncludeAssembly(validated.includeAssembly());
        build.setAssemblyFee(validated.assemblyFee());
        build.setTotalPrice(validated.totalPrice());
        build.setCreatedAt(LocalDateTime.now());
        AutoBuild saved = autoBuildRepository.save(build);

        saveAutoBuildItems(saved, validated);
        return toAutoBuildResponse(saved, validated.toItemDtos(), copiedOnWrite);
    }

    private void saveAutoBuildItems(AutoBuild build, ValidatedParts validated) {
        List<AutoBuildItem> items = validated.parts().stream().map(part -> {
            AutoBuildItem item = new AutoBuildItem();
            item.setAutoBuild(build);
            item.setProduct(part.product());
            item.setUnitPrice(part.product().getBasePrice());
            return item;
        }).toList();
        autoBuildItemRepository.saveAll(items);
    }

    private User resolveActorUser(Integer userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new UnauthenticatedException("유효하지 않은 회원 정보입니다."));
    }

    private void verifyAccess(User owner, Integer userId, String forbiddenMessage) {
        if (owner == null) return;
        if (userId == null) {
            throw new UnauthenticatedException("로그인이 필요합니다.");
        }
        if (owner.getUserId() != userId) {
            throw new AuthorizationFailureException(forbiddenMessage);
        }
    }

    private Map<Integer, ProductSpec> loadSpecMap(List<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productSpecRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductSpec::getProductId, Function.identity()));
    }

    private List<Product> filterByPartType(List<Product> products, String partType) {
        return products.stream()
                .filter(p -> partType.equals(p.getDerivedPartType()))
                .filter(p -> !"KEYCAP".equals(partType) || !isCustomKeycap(p))
                .toList();
    }

    private static boolean isCustomKeycap(Product product) {
        return product.getCategory() != null
                && "keycap-custom".equalsIgnoreCase(product.getCategory().getCategoryId());
    }

    private Product selectHousing(List<Product> housings, Map<Integer, ProductSpec> specMap, String layout) {
        if (housings.isEmpty()) {
            throw new InvalidStateException("추천 가능한 하우징 부품이 없습니다.");
        }
        return housings.stream()
                .filter(h -> layout != null && layout.trim().equalsIgnoreCase(specLayout(specMap.get(h.getProductId()))))
                .findFirst()
                .orElse(housings.get(0));
    }

    private Product selectPcb(
            List<Product> pcbs,
            Map<Integer, ProductSpec> specMap,
            String targetLayout,
            String connectivity
    ) {
        if (pcbs.isEmpty()) {
            throw new InvalidStateException("추천 가능한 기판 부품이 없습니다.");
        }
        boolean requireWireless = isWirelessRequested(connectivity);
        List<Product> layoutMatched = pcbs.stream()
                .filter(p -> targetLayout == null
                        || specLayout(specMap.get(p.getProductId())) == null
                        || targetLayout.equalsIgnoreCase(specLayout(specMap.get(p.getProductId()))))
                .toList();

        if (layoutMatched.isEmpty()) {
            return pcbs.get(0);
        }
        if (!requireWireless) {
            return layoutMatched.get(0);
        }
        return layoutMatched.stream()
                .filter(p -> {
                    ProductSpec spec = specMap.get(p.getProductId());
                    return spec != null && Boolean.TRUE.equals(spec.getIsWireless());
                })
                .findFirst()
                .orElse(layoutMatched.get(0));
    }

    private Product selectSwitch(
            List<Product> switches,
            Map<Integer, ProductSpec> specMap,
            List<Integer> pcbPins,
            String soundPref
    ) {
        if (switches.isEmpty()) {
            throw new InvalidStateException("추천 가능한 스위치 부품이 없습니다.");
        }
        List<Product> pinMatched = switches.stream()
                .filter(s -> {
                    ProductSpec spec = specMap.get(s.getProductId());
                    return spec == null || spec.getSwitchPin() == null || pcbPins.contains(spec.getSwitchPin());
                })
                .toList();

        List<Product> candidates = pinMatched.isEmpty() ? switches : pinMatched;
        return candidates.stream()
                .filter(s -> soundPref != null && soundPref.equalsIgnoreCase(inferSoundType(specMap.get(s.getProductId()), s.getDescription())))
                .findFirst()
                .orElse(candidates.get(0));
    }

    private Product selectKeycap(List<Product> keycaps, Map<Integer, ProductSpec> specMap, String materialPref) {
        if (keycaps.isEmpty()) {
            throw new InvalidStateException("추천 가능한 키캡 부품이 없습니다.");
        }
        if (materialPref == null || materialPref.isBlank()) {
            return keycaps.get(0);
        }
        return keycaps.stream()
                .filter(k -> {
                    ProductSpec spec = specMap.get(k.getProductId());
                    return spec != null && materialPref.trim().equalsIgnoreCase(spec.getKeycapMaterial());
                })
                .findFirst()
                .orElse(keycaps.get(0));
    }

    private static boolean isWirelessRequested(String connectivity) {
        if (connectivity == null) return false;
        String lower = connectivity.trim().toLowerCase(Locale.ROOT);
        return "wireless".equals(lower) || lower.contains("무선");
    }

    private static String inferSoundType(ProductSpec spec, String description) {
        String switchType = spec != null && spec.getSwitchType() != null
                ? spec.getSwitchType().trim().toLowerCase(Locale.ROOT)
                : "";
        if ("linear".equals(switchType) || switchType.contains("리니어")) return "thock";
        if ("tactile".equals(switchType) || switchType.contains("택타일")) return "clack";
        if ("silent".equals(switchType) || switchType.contains("저소음")) return "silent";
        if ("click".equals(switchType) || switchType.contains("클릭")) return "clicky";
        if (description != null) {
            if (description.contains("저소음")) return "silent";
            if (description.contains("클릭")) return "clicky";
            if (description.contains("택타일") || description.contains("갈축")) return "clack";
        }
        return "thock";
    }

    private static String specLayout(ProductSpec spec) {
        if (spec == null || spec.getLayout() == null || spec.getLayout().isBlank()) return null;
        return spec.getLayout().trim();
    }

    private static List<Integer> resolveSupportPins(ProductSpec spec) {
        List<Integer> parsed = spec != null ? ProductService.parseSupportPins(spec.getSupportPins()) : List.of();
        return parsed.isEmpty() ? DEFAULT_SUPPORT_PINS : parsed;
    }

    private static String normalizeName(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) return fallback;
        String trimmed = candidate.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private static BuildResponse toManualBuildResponse(
            ManualBuild build,
            List<BuildResponse.BuildItemDto> items,
            boolean copiedOnWrite
    ) {
        return BuildResponse.builder()
                .buildId(build.getManualBuildId())
                .buildType("MANUAL")
                .userId(build.getUser() != null ? build.getUser().getUserId() : null)
                .buildName(build.getBuildName())
                .includeAssembly(build.isIncludeAssembly())
                .assemblyFee(build.getAssemblyFee())
                .totalPrice(build.getTotalPrice())
                .copiedOnWrite(copiedOnWrite)
                .createdAt(build.getCreatedAt())
                .items(items)
                .build();
    }

    private static BuildResponse toAutoBuildResponse(
            AutoBuild build,
            List<BuildResponse.BuildItemDto> items,
            boolean copiedOnWrite
    ) {
        return BuildResponse.builder()
                .buildId(build.getAutoBuildId())
                .buildType("AUTO")
                .userId(build.getUser() != null ? build.getUser().getUserId() : null)
                .buildName(build.getBuildName())
                .includeAssembly(build.isIncludeAssembly())
                .assemblyFee(build.getAssemblyFee())
                .totalPrice(build.getTotalPrice())
                .copiedOnWrite(copiedOnWrite)
                .createdAt(build.getCreatedAt())
                .surveyLayout(build.getSurveyLayout())
                .surveyConnectivity(build.getSurveyConnectivity())
                .surveySound(build.getSurveySound())
                .surveyKeycap(build.getSurveyKeycap())
                .items(items)
                .build();
    }

    private static BuildResponse.BuildItemDto toBuildItemDto(
            Product product,
            ProductSpec spec,
            int unitPrice
    ) {
        String material = null;
        if (spec != null) {
            material = spec.getHousingMaterial() != null ? spec.getHousingMaterial() : spec.getKeycapMaterial();
        }
        return BuildResponse.BuildItemDto.builder()
                .partType(product.getDerivedPartType())
                .productId(product.getProductId())
                .productName(product.getName())
                .unitPrice(unitPrice)
                .thumbnailUrl(product.getThumbnailUrl())
                .layout(spec != null ? spec.getLayout() : null)
                .supportPins(spec != null ? ProductService.parseSupportPins(spec.getSupportPins()) : List.of())
                .switchPin(spec != null ? spec.getSwitchPin() : null)
                .switchType(spec != null ? spec.getSwitchType() : null)
                .isWireless(spec != null ? spec.getIsWireless() : null)
                .material(material)
                .build();
    }

    private record PartWithSpec(Product product, ProductSpec spec) {}

    private record ValidatedParts(
            PartWithSpec housing,
            PartWithSpec pcb,
            PartWithSpec sw,
            PartWithSpec keycap,
            boolean includeAssembly,
            int assemblyFee,
            int totalPrice
    ) {
        List<PartWithSpec> parts() {
            return List.of(housing, pcb, sw, keycap);
        }

        List<BuildResponse.BuildItemDto> toItemDtos() {
            List<BuildResponse.BuildItemDto> dtos = new ArrayList<>(4);
            for (PartWithSpec part : parts()) {
                dtos.add(toBuildItemDto(part.product(), part.spec(), part.product().getBasePrice()));
            }
            return dtos;
        }
    }
}
