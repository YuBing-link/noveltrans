package com.yumu.noveltranslator.port.out;

import com.yumu.noveltranslator.domain.model.User;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserDetailsFactoryPort {
    UserDetails createUserDetails(User user);
}
