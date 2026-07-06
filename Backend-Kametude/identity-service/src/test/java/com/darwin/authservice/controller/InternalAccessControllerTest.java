package com.darwin.authservice.controller;

import com.darwin.authservice.entity.Profile;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAccessControllerTest {
    @Mock UserRepository userRepository;
    @Mock ProfileRepository profileRepository;
    @InjectMocks InternalAccessController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalServiceToken", "internal-test-token");
    }

    @Test
    void payoutProfileReturnsPrivatePhoneToAuthenticatedService() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId))
                .thenReturn(Optional.of(Profile.builder().userId(userId).phone("677170446").build()));

        var response = controller.getPayoutProfile("internal-test-token", userId);

        assertThat(response.phone()).isEqualTo("677170446");
    }

    @Test
    void payoutProfileRejectsInvalidInternalToken() {
        assertThatThrownBy(() -> controller.getPayoutProfile("wrong-token", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }
}
