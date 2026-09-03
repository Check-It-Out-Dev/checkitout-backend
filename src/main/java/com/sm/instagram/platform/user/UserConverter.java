package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

@Component
public class UserConverter {

    private final UserRepository userRepository;

    public UserConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Converter<Long, User> toUserConverter() {
        // Null-safe: requiredness of user references is the entity/service
        // layer's call ("Company is required"), not a converter explosion.
        return ctx -> ctx.getSource() == null
                ? null
                : userRepository.findById(ctx.getSource())
                        .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", ctx.getSource()));
    }

    public Converter<User, Long> fromUserConverter() {
        return ctx -> {
            User user = ctx.getSource();
            return user != null ? user.getId() : null;
        };
    }
}
