package com.darwin.authservice.config;

import com.darwin.authservice.entity.User;
import com.darwin.authservice.repository.ProfileRepository;
import com.darwin.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BanStateSynchronizer implements ApplicationRunner {
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var usersToDisable = profileRepository.findAllByBannedTrue().stream()
                .map(profile -> userRepository.findById(profile.getUserId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(User::isEnabled)
                .peek(user -> user.setEnabled(false))
                .toList();
        if (!usersToDisable.isEmpty()) {
            userRepository.saveAll(usersToDisable);
        }
    }
}
