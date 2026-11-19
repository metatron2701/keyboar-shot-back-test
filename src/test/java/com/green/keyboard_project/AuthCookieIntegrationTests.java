package com.green.keyboard_project;

import com.green.keyboard_project.repository.UserRepository;
import com.green.keyboard_project.repository.RefreshTokenRepository;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full cookie authentication and withdrawal against the isolated in-memory test database. */
@SpringBootTest
@Transactional
class AuthCookieIntegrationTests {
 @Autowired WebApplicationContext context;
 @Autowired UserRepository users;
 @Autowired RefreshTokenRepository tokens;
 MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();}
 void signup(String email) throws Exception {
  mvc.perform(post("/api/users/signup").contentType("application/json").content("""
   {"email":"%s","password":"Keyboard123","name":"테스트 회원","phoneNumber":"01012345678",
    "defaultAddress":{"zonecode":"04524","roadAddress":"서울 세종대로","detailAddress":"101호"},
    "termsConsent":{"serviceTerms":true,"privacyPolicy":true}}
   """.formatted(email))).andExpect(status().isCreated());
 }
 Cookie[] login(String email)throws Exception{
  var res=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Keyboard123\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
  var headers=res.getHeaders("Set-Cookie");assertThat(headers).hasSize(2).allMatch(v->v.contains("HttpOnly")&&v.contains("SameSite=Lax"));
  return headers.stream().map(v->{var pair=v.split(";",2)[0].split("=",2);return new Cookie(pair[0],pair[1]);}).toArray(Cookie[]::new);
 }
 @Test void signupLoginRefreshWithdrawalAndCookieExpiry()throws Exception{
  String email="auth-flow@example.com";signup(email);var cookies=login(email);
  int userId=users.findByEmail(email).orElseThrow().getUserId();
  mvc.perform(get("/api/auth/me").cookie(cookies)).andExpect(status().isOk()).andExpect(jsonPath("$.data.user.email").value(email));
  mvc.perform(post("/api/auth/refresh").cookie(cookies)).andExpect(status().isNoContent());
  mvc.perform(post("/api/users/me/verify-password").cookie(cookies).contentType("application/json").content("{\"password\":\"Keyboard123\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(true));
  var response=mvc.perform(delete("/api/users/me").cookie(cookies).contentType("application/json").content("{\"password\":\"Keyboard123\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
  assertThat(response.getHeaders("Set-Cookie")).hasSize(2).allMatch(v->v.contains("Max-Age=0")&&v.contains("HttpOnly")&&v.contains("SameSite=Lax")&&v.contains("Path=/"));
  assertThat(users.findByEmail(email)).isEmpty();assertThat(tokens.count()).isZero();
  var withdrawn=users.findById(userId).orElseThrow();
  assertThat(withdrawn.isDeleted()).isTrue();
  assertThat(withdrawn.getDeletedAt()).isNotNull();
  assertThat(withdrawn.getEmail()).hasSize(50).endsWith("@account.invalid");
  assertThat(withdrawn.getName()).isEqualTo("탈퇴 회원");
  assertThat(withdrawn.getPhoneNumber()).isNull();
  mvc.perform(post("/api/auth/login").contentType("application/json")
    .content("{\"email\":\""+email+"\",\"password\":\"Keyboard123\"}"))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
  mvc.perform(post("/api/auth/refresh").cookie(cookies)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me").cookie(cookies)).andExpect(status().isUnauthorized());
 }
 @Test void wrongPasswordPreservesAccountTokensAndOtherMember() throws Exception {
  String email="withdraw-wrong@example.com";signup(email);var cookies=login(email);
  signup("withdraw-other@example.com");var otherCookies=login("withdraw-other@example.com");
  int id=users.findByEmail(email).orElseThrow().getUserId();
  long count=tokens.count();
  mvc.perform(post("/api/users/me/verify-password").cookie(cookies).contentType("application/json")
    .content("{\"password\":\"Wrong123\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(false));
  var failure=mvc.perform(delete("/api/users/me").cookie(cookies).contentType("application/json")
    .content("{\"password\":\"Wrong123\"}"))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false)).andReturn().getResponse();
  assertThat(failure.getHeaders("Set-Cookie")).isEmpty();
  assertThat(users.findById(id).orElseThrow().isDeleted()).isFalse();
  assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo(email);
  assertThat(tokens.count()).isEqualTo(count);
  mvc.perform(delete("/api/users/me").cookie(cookies).contentType("application/json")
    .content("{\"password\":\"Keyboard123\"}"))
    .andExpect(status().isOk());
  assertThat(tokens.count()).isEqualTo(count-1);
  assertThat(users.findByEmail("withdraw-other@example.com").orElseThrow().isDeleted()).isFalse();
  mvc.perform(post("/api/auth/refresh").cookie(otherCookies)).andExpect(status().isNoContent());
  mvc.perform(get("/api/auth/me").cookie(otherCookies)).andExpect(status().isOk());
 }
 @Test void forgedAccessCookieCannotVerifyOrWithdraw() throws Exception {
  signup("forged-member@example.com");
  for (var request : java.util.List.of(post("/api/users/me/verify-password"),delete("/api/users/me"))) {
   mvc.perform(request.cookie(new Cookie("access_token","invalid.jwt.value")).contentType("application/json")
     .content("{\"password\":\"Keyboard123\"}"))
     .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
  }
  assertThat(users.findByEmail("forged-member@example.com").orElseThrow().isDeleted()).isFalse();
 }
 @Test void logoutRevokesRefreshAndAnonymousCannotWithdraw()throws Exception{
  signup("logout@example.com");var cookies=login("logout@example.com");
  mvc.perform(post("/api/auth/logout").cookie(cookies)).andExpect(status().isNoContent());
  assertThat(tokens.count()).isZero();
  mvc.perform(post("/api/auth/refresh").cookie(cookies)).andExpect(status().isUnauthorized());
  mvc.perform(delete("/api/users/me").contentType("application/json").content("{\"password\":\"Keyboard123\"}"))
   .andExpect(status().isUnauthorized());
 }
}
