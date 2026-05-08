package com.yumu.noveltranslator.adapter.out.security;

import com.yumu.noveltranslator.domain.model.User;
import com.yumu.noveltranslator.port.out.UserDetailsFactoryPort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class UserDetailsFactoryAdapter implements UserDetailsFactoryPort {

    @Override
    public UserDetails createUserDetails(User user) {
        return new CustomUserDetails(user);
    }
}
