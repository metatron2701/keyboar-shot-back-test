package com.green.keyboard_project.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    // Api가 반환하는 데이터의 형이 다 다름
    // T자리에 Void, String, User등 모든 데이터 형태가 올 수 있음
    private T data;


    // 생성자로 만들지 않고 굳이 ok, fail 등 정적 팩토리 메서드를 사용하는 이유:
    // - 호출하는 곳에서 내부 생성자 구조를 몰라도 직관적으로 사용 가능
    // - 향후 ApiResponse 구조가 변경되어도 호출부 코드 수정을 최소화할 수 있음

    //빌더 패턴의 장점
    //생성자 오버로딩이 필요없다(줄일 수 있음)
    //객체 생성 코드만 봐도 어느 필드에 뭐가 들어가는지 알 수 있다(가독성이 좋음)

    // 성공 (데이터 O)
    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }
    // 성공 (데이터 X)
    public static <T> ApiResponse<T> ok() {
        return ApiResponse.<T>builder()
                .success(true)
                .build();
    }
    // 실패
    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
