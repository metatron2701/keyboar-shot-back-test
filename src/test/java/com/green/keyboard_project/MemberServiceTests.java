package com.green.keyboard_project;

import com.green.keyboard_project.entity.User;
import com.green.keyboard_project.exceptions.*;
import com.green.keyboard_project.repository.*;
import com.green.keyboard_project.security.WithdrawalAuth;
import com.green.keyboard_project.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberServiceTests {
    @Test void missingAuthenticationIntegrationFailsBeforeAccountMutation() {
        var users = mock(UserRepository.class);
        var addresses = mock(AddressRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<WithdrawalAuth> provider = mock(ObjectProvider.class);
        var user = new User();
        user.setUserId(7);
        user.setEmail("member@example.com");
        user.setPassword(new BCryptPasswordEncoder().encode("Keyboard123"));
        when(users.findByIdForWithdrawal(7)).thenReturn(Optional.of(user));
        var service = new UserService(users,null,provider,new BCryptPasswordEncoder());
        assertThatThrownBy(() -> service.withdraw(7,"Keyboard123")).isInstanceOf(InvalidStateException.class);
        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getEmail()).isEqualTo("member@example.com");
        verifyNoInteractions(addresses);
    }

    @Test void incorrectPasswordDoesNotMutateAccountOrRevokeTokens() {
        var users = mock(UserRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<WithdrawalAuth> provider = mock(ObjectProvider.class);
        var auth = mock(WithdrawalAuth.class);
        when(provider.getIfAvailable()).thenReturn(auth);
        when(auth.isPasswordMember(7)).thenReturn(true);
        var user = new User();
        user.setUserId(7);
        user.setEmail("member@example.com");
        user.setPassword(new BCryptPasswordEncoder().encode("Keyboard123"));
        when(users.findByIdForWithdrawal(7)).thenReturn(Optional.of(user));
        var service = new UserService(users,null,provider,new BCryptPasswordEncoder());
        assertThatThrownBy(() -> service.withdraw(7,"Wrong123")).isInstanceOf(AuthorizationFailureException.class);
        assertThat(user.isDeleted()).isFalse();
        assertThat(user.getEmail()).isEqualTo("member@example.com");
        verify(auth,never()).revokeRefreshTokens(anyInt());
    }
}
